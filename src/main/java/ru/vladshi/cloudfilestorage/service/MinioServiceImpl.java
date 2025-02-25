package ru.vladshi.cloudfilestorage.service;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.dto.StorageItem;
import ru.vladshi.cloudfilestorage.exception.*;

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
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;
    private final String usersBucketName;

    @Autowired
    public MinioServiceImpl(MinioClient minioClient, @Value("${minio.bucket.users}") String bucketName) {
        this.minioClient = minioClient;
        this.usersBucketName = bucketName;
    }

    @PostConstruct
    public void init() {
        try {
            boolean isBucketExist = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(usersBucketName).build());
            if (!isBucketExist) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(usersBucketName).build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<StorageItem> getItems(String userPrefix, String path) {
        List<StorageItem> items = new ArrayList<>();

        String fullPrefix = buildFullPrefix(userPrefix, path);

        try {
            checkFolderExists(userPrefix, fullPrefix, path);

            Iterable<Result<Item>> foundItems = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .startAfter(fullPrefix)
                            .prefix(fullPrefix)
                            .delimiter("/")
                            .recursive(false)
                            .build()
            );

            for (Result<Item> foundItem : foundItems) {
                Item item = foundItem.get();
                String fullItemPath = item.objectName();
                String relativePath = fullItemPath.substring(userPrefix.length());
                boolean isFolder = fullItemPath.endsWith("/");

                items.add(new StorageItem(relativePath, isFolder, item.size()));
            }
        } catch (FolderNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list user items", e);
        }

        return items;
    }

    @Override
    public void createFolder(String userPrefix, String path, String newFolderName) {
        validateInputName(newFolderName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullNewFolderPath = fullPrefix + newFolderName + "/";

        try {
            checkFolderExists(userPrefix, fullPrefix, path);

            checkFolderNotExists(fullNewFolderPath, newFolderName);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullNewFolderPath)
                            .stream(getEmptyStream(), 0, -1)
                            .build()
            );
        } catch (FolderNotFoundException | FolderAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create folder: " + fullNewFolderPath, e);
        }
    }

    @Override
    public void deleteFolder(String userPrefix, String path, String folderToDeleteName) {
        validateInputName(folderToDeleteName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String folderToDeleteFullPath = fullPrefix + folderToDeleteName + "/";

        try {
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
                throw new FolderNotFoundException("Folder does not exist: " + path + folderToDeleteName);
            }

            batchDeleteObjects(userPrefix, ItemsToDelete);

        } catch (FolderNotFoundException | ObjectDeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete folder: " + path + folderToDeleteName + "/", e);
        }
    }

    @Override
    public void renameFolder(String userPrefix, String path, String oldFolderName, String newFolderName) {
        validateInputName(oldFolderName);
        validateInputName(newFolderName);
        if (oldFolderName.equals(newFolderName)) {
            return;
        }

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullOldPath = fullPrefix + oldFolderName + "/";
        String fullNewPath = fullPrefix + newFolderName + "/";

        try {
            checkFolderNotExists(fullNewPath, newFolderName);

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
                throw new FolderNotFoundException("Folder does not exist: " + path + oldFolderName);
            }

            batchDeleteObjects(userPrefix, itemsToDelete);

        } catch (FolderNotFoundException | ObjectDeletionException | FolderAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to rename folder: " + path + oldFolderName + "/", e);
        }
    }

    @Override
    public void uploadFile(String userPrefix, String path, MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("File cannot be null or nameless. Choose a file to upload.");
        }

        String fileName = file.getOriginalFilename();

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullFilePath = fullPrefix + fileName;

        try {
            checkFileNotExists(fullFilePath);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullFilePath)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .build()
            );
        } catch (FileAlreadyExistsInStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file: " + fullFilePath, e);
        }
    }

    @Override
    public void deleteFile(String userPrefix, String path, String fileToDeleteName) {
        validateInputName(fileToDeleteName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullFilePath = fullPrefix + fileToDeleteName;

        try {
            checkFileExists(fullFilePath);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullFilePath).build()
            );

        } catch (FileNotFoundInStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file: " + fullFilePath, e);
        }
    }

    @Override
    public void renameFile(String userPrefix, String path, String oldFileName, String newFileName) {
        validateInputName(oldFileName);
        validateInputName(newFileName);

        if (oldFileName.equals(newFileName)) {
            return;
        }

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullOldFilePath = fullPrefix + oldFileName;
        String fullNewFilePath = fullPrefix + newFileName;

        try {
            checkFileExists(fullOldFilePath);
            checkFileNotExists(fullNewFilePath);

            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullNewFilePath)
                            .source(CopySource.builder()
                                    .bucket(usersBucketName)
                                    .object(fullOldFilePath)
                                    .build())
                            .build()
            );

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullOldFilePath)
                            .build());

        } catch (FileNotFoundInStorageException | FileAlreadyExistsInStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to rename file: " + fullOldFilePath, e);
        }
    }

    @Override
    public void uploadFolder(String userPrefix, String path, String folderToUploadName, MultipartFile[] files) {
        if (files == null || files.length == 0) { // TODO проверить что будет при загрузке пустой папки
            throw new IllegalArgumentException("Files cannot be null or empty");
        }

        validateInputName(folderToUploadName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullUploadedFolderPath = fullPrefix + folderToUploadName + "/";

        try {
            checkFolderExists(userPrefix, fullPrefix, path);
            checkFolderNotExists(fullUploadedFolderPath, folderToUploadName);

            List<SnowballObject> objectsToUpload = prepareUploadObjects(files, fullPrefix);

            minioClient.uploadSnowballObjects(
                    UploadSnowballObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .objects(objectsToUpload)
                            .build()
            );
        } catch (FolderNotFoundException | FolderAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload folder: " + folderToUploadName, e);
        }
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
    public InputStreamResource downloadFile(String userPrefix, String path, String fileName) {
        validateInputName(fileName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullFilePath = fullPrefix + fileName;

        try {
            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullFilePath)
                            .build()
            );

            return new InputStreamResource(inputStream);

        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                throw new FileNotFoundInStorageException("File not found: " +
                        (path == null ? fileName : path + fileName));
            }
            throw new RuntimeException("Failed to download file: " + fullFilePath, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file: " + fullFilePath, e);
        }
    }

    @Override
    public InputStreamResource downloadFolder(String userPrefix, String path, String folderName) {
        validateInputName(folderName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullFolderPath = fullPrefix + folderName + "/";

        Path tempZipFile = null;

        try {
            Iterable<Result<Item>> ItemsToDownload = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .startAfter(fullFolderPath)
                            .prefix(fullFolderPath)
                            .delimiter("/")
                            .recursive(true)
                            .build()
            );

            boolean ItemsToDownloadAreEmpty = !ItemsToDownload.iterator().hasNext();
            if (ItemsToDownloadAreEmpty) {
                throw new FolderNotFoundException(
                        "Folder does not exist: " + (path == null ? folderName : path + folderName + "/"));
            }

            tempZipFile = createZipArchive(ItemsToDownload, fullFolderPath);

            return new InputStreamResource(Files.newInputStream(tempZipFile));

        } catch (FolderNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to download folder: " + fullFolderPath, e);
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
//            log.error("Failed to create ZIP archive for folder: {}", parentFolderFullPath, e);
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
                // Логируем ошибку удаления файла (если она произошла)
            }
        }
    }

    @Override
    public List<StorageItem> searchItems(String userPrefix, String query) {
        List<StorageItem> itemsThatMatch = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return itemsThatMatch;
        }

        try {
            Iterable<Result<Item>> allUserItems = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .prefix(userPrefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> itemResult : allUserItems) {
                Item item = itemResult.get();
                String fullItemPath = item.objectName();
                boolean isFolder = fullItemPath.endsWith("/");

                String fileName = extractNameFromPath(fullItemPath);

                if (fileName.toLowerCase().contains(query.toLowerCase())) {
                    String relativePath = fullItemPath.substring(userPrefix.length());
                    itemsThatMatch.add(new StorageItem(relativePath, isFolder, item.size()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to search by: " + query, e);
        }

        return itemsThatMatch;
    }

    private void checkFileExists(String fullFilePath) throws Exception {
        if (!fileExists(fullFilePath)) {
            throw new FileNotFoundInStorageException("File not found: " + extractNameFromPath(fullFilePath));
        }
    }

    private void checkFileNotExists(String fullFilePath) throws Exception {
        if (fileExists(fullFilePath)) {
            throw new FileAlreadyExistsInStorageException("File already exists: " + extractNameFromPath(fullFilePath));
        }
    }

    private boolean fileExists(String filePath) throws Exception {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(filePath)
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

    private void checkFolderExists(
            String userPrefix, String fullFolderPath, String folderRelativePath) throws Exception {
        boolean isNotUserRootFolder = fullFolderPath.length() > userPrefix.length() + 1;
        if (isNotUserRootFolder && !folderExists(fullFolderPath)) {
            throw new FolderNotFoundException("Folder does not exist: " + folderRelativePath);
        }
    }

    private void checkFolderNotExists(String fullFolderPath, String folderName) throws Exception {
        if (folderExists(fullFolderPath)) {
            throw new FolderAlreadyExistsException("Folder already exists: " + folderName);
        }
    }

    private boolean folderExists(String folderPath) throws Exception {
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

    private void batchDeleteObjects(String userPrefix, List<DeleteObject> objectsToDelete) throws Exception {
        if (!objectsToDelete.isEmpty()) {
            Iterable<Result<DeleteError>> deletingResults = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .objects(objectsToDelete)
                            .build()
            );

            for (Result<DeleteError> result : deletingResults) {
                DeleteError error = result.get();
                throw new ObjectDeletionException(
                        "Failed to delete object: " + error.objectName().substring(userPrefix.length()));
            }
        }
    }

    private ByteArrayInputStream getEmptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private void validateInputName(String inputName) {
        if (inputName == null || inputName.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
    }

    private String buildFullPrefix(String userPrefix, String folderPath) {
        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }
        return fullPrefix;
    }

    private String extractNameFromPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        Path path = Paths.get(fullPath);
        return path.getFileName() != null ? path.getFileName().toString() : "/";
    }
}
