package ru.vladshi.cloudfilestorage.service;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MinioServiceImpl.class);
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
            log.error("Failed to initialize bucket for users: {}", usersBucketName, e);
            throw new RuntimeException("Users bucket initialization failed", e);
        }
    }

    @Override
    public List<StorageItem> getItems(String userPrefix, String path) throws Exception {
        List<StorageItem> items = new ArrayList<>();

        String fullPrefix = buildFullPrefix(userPrefix, path);

        checkFolderExists(userPrefix, fullPrefix, path);
        /*
         TODO кажется лишний сетевой запрос. Подумать как избавиться. Если в найденных элементах нету пустого итема
          отображающего папку то папка не существует. (Только надо тогда убрать startAfter())
        */

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

        return items;
    }

    @Override
    public void createFolder(String userPrefix, String path, String newFolderName) throws Exception {
        validateInputName(newFolderName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullNewFolderPath = fullPrefix + newFolderName + "/";

        checkFolderExists(userPrefix, fullPrefix, path);

        checkFolderNotExists(fullNewFolderPath);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullNewFolderPath)
                        .stream(getEmptyStream(), 0, -1)
                        .build()
        );
    }

    @Override
    public void deleteFolder(String userPrefix, String path, String folderToDeleteName) throws Exception {
        validateInputName(folderToDeleteName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String folderToDeleteFullPath = fullPrefix + folderToDeleteName + "/";

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
    }

    @Override
    public void renameFolder(String userPrefix, String path, String oldFolderName, String newFolderName)
            throws Exception {
        validateInputName(oldFolderName);
        validateInputName(newFolderName);
        if (oldFolderName.equals(newFolderName)) {
            return;
        }

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullOldPath = fullPrefix + oldFolderName + "/";
        String fullNewPath = fullPrefix + newFolderName + "/";

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
            throw new FolderNotFoundException("Folder does not exist: " + path + oldFolderName);
        }

        batchDeleteObjects(userPrefix, itemsToDelete);
    }

    @Override
    public void uploadFile(String userPrefix, String path, MultipartFile file) throws Exception {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("File cannot be null or nameless. Choose a file to upload.");
        }

        String fileName = file.getOriginalFilename();

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullFilePath = fullPrefix + fileName;

        checkFileNotExists(fullFilePath);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullFilePath)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .build()
        );
    }

    @Override
    public void deleteFile(String userPrefix, String path, String fileToDeleteName) throws Exception {
        validateInputName(fileToDeleteName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullFilePath = fullPrefix + fileToDeleteName;

        checkFileExists(fullFilePath);

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullFilePath).build()
        );
    }

    @Override
    public void renameFile(String userPrefix, String path, String oldFileName, String newFileName) throws Exception {
        validateInputName(oldFileName);
        validateInputName(newFileName);

        if (oldFileName.equals(newFileName)) {
            return;
        }

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullOldFilePath = fullPrefix + oldFileName;
        String fullNewFilePath = fullPrefix + newFileName;

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
    }

    @Override
    public void uploadFolder(String userPrefix, String path, String folderToUploadName, MultipartFile[] files)
            throws Exception {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Folder cannot be null or empty. Choose not empty folder.");
        }

        validateInputName(folderToUploadName);

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullUploadedFolderPath = fullPrefix + folderToUploadName + "/";

        checkFolderExists(userPrefix, fullPrefix, path);
        checkFolderNotExists(fullUploadedFolderPath);

        List<SnowballObject> objectsToUpload = prepareUploadObjects(files, fullPrefix);

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
    public InputStreamResource downloadFile(String userPrefix, String path, String fileName) throws Exception {
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
            throw e;
        }
    }

    @Override
    public InputStreamResource downloadFolder(String userPrefix, String path, String folderName) throws Exception {

        String fullPrefix = buildFullPrefix(userPrefix, path);
        String fullFolderPath = fullPrefix + folderName + "/";

        Path tempZipFile = null;

        try {
            checkFolderExists(userPrefix, fullFolderPath, path == null ? folderName : path + folderName);

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

    @Override
    public List<StorageItem> searchItems(String userPrefix, String query) throws Exception {
        List<StorageItem> itemsThatMatch = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return itemsThatMatch;
        }

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

            String itemName = extractNameFromPath(fullItemPath);

            if (itemName.toLowerCase().contains(query.toLowerCase())) {
                String relativePath = fullItemPath.substring(userPrefix.length());
                itemsThatMatch.add(new StorageItem(relativePath, isFolder, item.size()));
            }
        }

        return itemsThatMatch;
    }

    private void checkFileExists(String fullFilePath) throws Exception {
        if (!fileExists(fullFilePath)) {
            throw new FileNotFoundInStorageException("File does not exist: " + extractNameFromPath(fullFilePath));
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

    private void checkFolderNotExists(String fullFolderPath) throws Exception {
        if (folderExists(fullFolderPath)) {
            throw new FolderAlreadyExistsException("Folder already exists: " + extractNameFromPath(fullFolderPath));
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
                Exception objectDeletionException = new ObjectDeletionException(
                        "Failed to delete object: " + error.objectName().substring(userPrefix.length()));
                log.error("Failed to delete object: {}. {}",
                        error.objectName(), error.message(), objectDeletionException);
                throw objectDeletionException;
            }
        }
    }

    private ByteArrayInputStream getEmptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private void validateInputName(String inputName) {
        if (inputName == null || inputName.isBlank()) {
            throw new InputNameValidationException("Name cannot be null or empty");
        }
        if (inputName.indexOf('/') != -1) {
            throw new InputNameValidationException("Name cannot contains '/'");
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
        return fullPrefix; // TODO ограничение максимальной длинны (в минио есть ограничение)
    }

    private String extractNameFromPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        Path path = Paths.get(fullPath);
        return path.getFileName() != null ? path.getFileName().toString() : "/";
    }
}
