package ru.vladshi.cloudfilestorage.storage.service.impl;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.storage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.storage.exception.FolderNotFoundException;
import ru.vladshi.cloudfilestorage.storage.exception.FolderUploadingException;
import ru.vladshi.cloudfilestorage.storage.exception.ObjectDeletionException;
import ru.vladshi.cloudfilestorage.storage.model.StorageItem;
import ru.vladshi.cloudfilestorage.storage.service.AbstractMinioService;
import ru.vladshi.cloudfilestorage.storage.service.FolderService;
import ru.vladshi.cloudfilestorage.storage.util.PathUtil;
import ru.vladshi.cloudfilestorage.storage.validation.StorageItemNameValidator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class MinioFolderServiceImpl extends AbstractMinioService implements FolderService {

    @Autowired
    public MinioFolderServiceImpl(MinioClientProvider minioClientProvider) {
        super(minioClientProvider);
    }

    @Override
    public void createUserRootFolder(String userFolderName) throws Exception {
        if (userFolderName == null || userFolderName.isBlank()) {
            throw new IllegalArgumentException("userFolderName cannot be null or empty");
        }

        checkFolderNotExists(userFolderName);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(userFolderName)
                        .stream(getEmptyStream(), 0, -1)
                        .build()
        );
    }

    @Override
    public List<StorageItem> getFolderContents(String path) throws Exception {
        List<StorageItem> items = new ArrayList<>();

        Iterable<Result<Item>> foundItems = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(usersBucketName)
                        .prefix(path)
                        .delimiter("/")
                        .recursive(false)
                        .build()
        );

        if (!foundItems.iterator().hasNext()) {
            throw new FolderNotFoundException(PathUtil.removeRootFolder(path));
        }

        for (Result<Item> foundItem : foundItems) {
            Item item = foundItem.get();
            String itemPath = item.objectName();
            String relativePath = PathUtil.removeRootFolder(itemPath);
            boolean isFolder = relativePath.endsWith("/");

            if (!itemPath.equals(path)) {
                items.add(new StorageItem(relativePath, isFolder, item.size()));
            }
        }

        return items;
    }

    @Override
    public void create(String path, String newFolderName) throws Exception {
        StorageItemNameValidator.validate(newFolderName);

        checkFolderExists(path);

        String newFolderPath = path + newFolderName + "/";
        checkFolderNotExists(newFolderPath);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(newFolderPath)
                        .stream(getEmptyStream(), 0, -1)
                        .build()
        );
    }

    @Override
    public void delete(String path, String folderToDeleteName) throws Exception {
        StorageItemNameValidator.validate(folderToDeleteName);

        String folderToDeleteFullPath = path + folderToDeleteName + "/";

        Iterable<Result<Item>> foundItems = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(usersBucketName)
                        .prefix(folderToDeleteFullPath)
                        .recursive(true)
                        .build()
        );

        List<DeleteObject> ItemsToDelete = new ArrayList<>();
        for (Result<Item> item : foundItems) {
            ItemsToDelete.add(new DeleteObject(item.get().objectName()));
        }

        if (ItemsToDelete.isEmpty()) {
            throw new FolderNotFoundException(PathUtil.removeRootFolder(folderToDeleteFullPath));
        }

        batchDeleteObjects(ItemsToDelete);
    }

    @Override
    public void rename(String path, String oldFolderName, String newFolderName) throws Exception {
        StorageItemNameValidator.validate(oldFolderName);
        StorageItemNameValidator.validate(newFolderName);
        if (oldFolderName.equals(newFolderName)) {
            return;
        }

        String fullOldPath = path + oldFolderName + "/";
        String fullNewPath = path + newFolderName + "/";

        checkFolderNotExists(fullNewPath);

        Iterable<Result<Item>> ItemsToRename = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(usersBucketName)
                        .prefix(fullOldPath)
                        .recursive(true)
                        .build()
        );

        List<DeleteObject> itemsToDelete = new ArrayList<>();

        for (Result<Item> itemToRename : ItemsToRename) {
            String oldObjectFullName = itemToRename.get().objectName();

            itemsToDelete.add(new DeleteObject(oldObjectFullName));

            String newObjectFullName = fullNewPath + oldObjectFullName.substring(fullOldPath.length());

            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(newObjectFullName)
                            .source(CopySource.builder()
                                    .bucket(usersBucketName)
                                    .object(oldObjectFullName)
                                    .build())
                            .build()
            );
        }

        if (itemsToDelete.isEmpty()) {
            throw new FolderNotFoundException(PathUtil.removeRootFolder(fullOldPath));
        }

        batchDeleteObjects(itemsToDelete);
    }

    @Override
    public void upload(String path, String folderToUploadName, MultipartFile[] files) throws Exception {
        if (files == null || files.length == 0) {
            throw new FolderUploadingException("Folder cannot be null or empty. Choose not empty folder.");
        }

        StorageItemNameValidator.validate(folderToUploadName);

        String fullUploadedFolderPath = path + folderToUploadName + "/";

        checkFolderExists(path);
        checkFolderNotExists(fullUploadedFolderPath);

        List<SnowballObject> objectsToUpload = prepareUploadObjects(files, path);

        minioClient.uploadSnowballObjects(
                UploadSnowballObjectsArgs.builder()
                        .bucket(usersBucketName)
                        .objects(objectsToUpload)
                        .build()
        );
    }

    private List<SnowballObject> prepareUploadObjects(MultipartFile[] files, String fullPrefix) throws IOException {
        List<SnowballObject> objectsToUpload = new ArrayList<>();
        Set<String> foldersToCreate = new HashSet<>();

        for (MultipartFile file : files) {
            if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
                throw new IllegalArgumentException("File cannot be null or nameless");
            }

            String fileFullPath = fullPrefix + file.getOriginalFilename();

            addParentFolders(foldersToCreate, fileFullPath);

            objectsToUpload.add(
                    new SnowballObject(
                            fileFullPath,
                            file.getInputStream(),
                            file.getSize(),
                            null)
            );
        }

        for (String folderPath : foldersToCreate) {
            objectsToUpload.add(
                    new SnowballObject(
                            folderPath,
                            getEmptyStream(),
                            0,
                            null)
            );
        }

        return objectsToUpload;
    }

    private static void addParentFolders(Set<String> foldersToCreate, String fileFullPath) {
        Path parent = Paths.get(fileFullPath).getParent();
        while (parent != null && foldersToCreate.add(parent.toString() + "/")) {
            parent = parent.getParent();
        }
    }

    @Override
    public InputStreamResource download(String path, String folderName) throws Exception {
        String fullFolderPath = path + folderName + "/";

        Path tempZipFile = null;

        try {
            checkFolderExists(path);

            Iterable<Result<Item>> ItemsToDownload = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .startAfter(fullFolderPath)
                            .prefix(fullFolderPath)
                            .delimiter("/")
                            .recursive(true)
                            .build()
            );

            tempZipFile = createZipArchive(ItemsToDownload, fullFolderPath);

            return new InputStreamResource(Files.newInputStream(tempZipFile));

        } finally {
            cleanupTempFile(tempZipFile);
        }
    }

    private Path createZipArchive(
            Iterable<Result<Item>> items, String parentFolderFullPath) throws Exception {

        Path tempZipFile = Files.createTempFile("folder-", ".zip");

        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(tempZipFile))) {
            Set<String> addedFolders = new HashSet<>();

            for (Result<Item> item : items) {
                String objectName = item.get().objectName();
                String relativeName = objectName.substring(parentFolderFullPath.length());
                boolean isFolder = objectName.endsWith("/");

                if (isFolder) {
                    if (addedFolders.add(relativeName)) {
                        addZipEntry(zipOut, relativeName, null);
                    }
                } else {
                    try (InputStream inputStream = minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(usersBucketName)
                                    .object(objectName)
                                    .build()
                    )) {
                        addZipEntry(zipOut, relativeName, inputStream);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to create ZIP archive for folder: {}", parentFolderFullPath, e);
            throw new RuntimeException("Failed to create ZIP archive for folder: " + parentFolderFullPath, e);
        }

        return tempZipFile;
    }

    private void addZipEntry(ZipOutputStream zipOut, String entryName, InputStream inputStream) throws IOException {
        zipOut.putNextEntry(new ZipEntry(entryName));
        if (inputStream != null) {
            inputStream.transferTo(zipOut);
        }
        zipOut.closeEntry();
    }

    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.error("Failed to delete temp file: {}", tempFile, e);
            }
        }
    }

    private void checkFolderExists(String path) throws Exception {
        if (!folderExists(path)) {
            throw new FolderNotFoundException(PathUtil.removeRootFolder(path));
        }
    }

    private void checkFolderNotExists(String fullFolderPath) throws Exception {
        if (folderExists(fullFolderPath)) {
            throw new FolderAlreadyExistsException(PathUtil.extractNameFromPath(fullFolderPath));
        }
    }

    private boolean folderExists(String folderPath) throws Exception {
        if (folderPath.endsWith("//")) {
            return false;
        }
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(folderPath)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            throw e;
        }
    }

    private void batchDeleteObjects(List<DeleteObject> objectsToDelete) throws Exception {
        if (!objectsToDelete.isEmpty()) {
            Iterable<Result<DeleteError>> deletingResults = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .objects(objectsToDelete)
                            .build()
            );

            for (Result<DeleteError> result : deletingResults) {
                DeleteError error = result.get();
                String relativePath = PathUtil.removeRootFolder(error.objectName());
                Exception objectDeletionException = new ObjectDeletionException(relativePath);
                log.error("Failed to delete object: {}. {}",
                        error.objectName(), error.message(), objectDeletionException);
                throw objectDeletionException;
            }
        }
    }

    private ByteArrayInputStream getEmptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }
}
