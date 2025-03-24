package ru.vladshi.cloudfilestorage.service.impl;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.model.StorageItem;
import ru.vladshi.cloudfilestorage.exception.*;
import ru.vladshi.cloudfilestorage.model.UserStorageInfo;
import ru.vladshi.cloudfilestorage.service.MinioService;
import ru.vladshi.cloudfilestorage.util.SizeFormatter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;
    private final String usersBucketName;
    @Value("${storage.max-size-per-user:40MB}")
    private String maxSizePerUser;

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
    public List<StorageItem> getItems(String path) throws Exception {
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
            throw new FolderNotFoundException("Folder does not exist.");
        }

        for (Result<Item> foundItem : foundItems) {
            Item item = foundItem.get();
            String itemPath = item.objectName();
            String securelyPath = itemPath.substring(itemPath.indexOf('/') + 1);
            boolean isFolder = securelyPath.endsWith("/");

            if (!itemPath.equals(path)) {
                items.add(new StorageItem(securelyPath, isFolder, item.size()));
            }
        }

        return items;
    }

    @Override
    public void createFolder(String path, String newFolderName) throws Exception {
        validateInputName(newFolderName);

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
    public void deleteFolder(String path, String folderToDeleteName) throws Exception {
        validateInputName(folderToDeleteName);

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
            throw new FolderNotFoundException("Folder does not exist: " + path + folderToDeleteName);
        }

        batchDeleteObjects(ItemsToDelete);
    }

    @Override
    public void renameFolder(String path, String oldFolderName, String newFolderName)
            throws Exception {
        validateInputName(oldFolderName);
        validateInputName(newFolderName);
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
            String securelyPath = path.substring(path.indexOf('/') + 1);
            throw new FolderNotFoundException("Folder does not exist: " + securelyPath + oldFolderName);
        }

        batchDeleteObjects(itemsToDelete);
    }

    @Override
    public void uploadFile(String path, MultipartFile file) throws Exception {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("File cannot be null or nameless. Choose a file to upload.");
        }

        String fileName = file.getOriginalFilename();

        String fullFilePath = path + fileName;

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
    public void deleteFile(String path, String fileToDeleteName) throws Exception {
        validateInputName(fileToDeleteName);

        String fullFilePath = path + fileToDeleteName;

        checkFileExists(fullFilePath);

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullFilePath).build()
        );
    }

    @Override
    public void renameFile(String path, String oldFileName, String newFileName) throws Exception {
        validateInputName(oldFileName);
        validateInputName(newFileName);

        if (oldFileName.equals(newFileName)) {
            return;
        }

        String fullOldFilePath = path + oldFileName;
        String fullNewFilePath = path + newFileName;

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
    public void uploadFolder(String path, String folderToUploadName, MultipartFile[] files)
            throws Exception {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Folder cannot be null or empty. Choose not empty folder.");
        }

        validateInputName(folderToUploadName);

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
    public InputStreamResource downloadFile(String path, String fileName) throws Exception {
        String fullFilePath = path + fileName;

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
                throw new FileNotFoundInStorageException("File not found: " + fileName);
            }
            throw e;
        }
    }

    @Override
    public InputStreamResource downloadFolder(String path, String folderName) throws Exception {

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

    @Override
    public long getFileSize(String path, String fileName) throws Exception {
        String fullFilePath = path + fileName;
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullFilePath)
                        .build()
        );
        return stat.size();
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

    private void checkFolderExists(String path) throws Exception {
        if (!folderExists(path)) {
            throw new FolderNotFoundException("Folder does not exist.");
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
                String securelyPath = error.objectName().substring(error.objectName().indexOf('/') + 1);
                Exception objectDeletionException = new ObjectDeletionException(
                        "Failed to delete object: " + securelyPath);
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

    private String extractNameFromPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        Path path = Paths.get(fullPath);
        return path.getFileName() != null ? path.getFileName().toString() : "/";
    }

    @Override
    public UserStorageInfo getUserStorageInfo(String userPrefix) throws Exception {
        long currentSize = getUserStorageSize(userPrefix);
        long maxSize = getMaxStorageSize();
        return new UserStorageInfo(currentSize, maxSize);
    }

    private long getUserStorageSize(String userPrefix) throws Exception {
        long totalSize = 0;
        Iterable<Result<Item>> objects = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(usersBucketName)
                        .prefix(userPrefix)
                        .recursive(true)
                        .build()
        );

        for (Result<Item> result : objects) {
            Item item = result.get();
            if (!item.isDir()) {
                totalSize += item.size();
            }
        }
        return totalSize;
    }

    public long getMaxStorageSize() {
        return parseSize(maxSizePerUser);
    }

    private long parseSize(String sizeStr) {
        sizeStr = sizeStr.trim().toUpperCase();
        if (sizeStr.endsWith("MB")) {
            return Long.parseLong(sizeStr.replace("MB", "")) * 1024 * 1024;
        } else if (sizeStr.endsWith("GB")) {
            return Long.parseLong(sizeStr.replace("GB", "")) * 1024 * 1024 * 1024;
        } else if (sizeStr.endsWith("KB")) {
            return Long.parseLong(sizeStr.replace("KB", "")) * 1024;
        } else {
            return Long.parseLong(sizeStr);
        }
    }

    @Override
    public void checkStorageLimit(String userPrefix, MultipartFile file) throws Exception {
        long uploadSize = file.getSize();
        checkStorageLimit(userPrefix, uploadSize);
    }

    @Override
    public void checkStorageLimit(String userPrefix, MultipartFile[] files) throws Exception {
        long uploadSize = Arrays.stream(files)
                .filter(file -> file != null)
                .mapToLong(MultipartFile::getSize)
                .sum();
        checkStorageLimit(userPrefix, uploadSize);
    }

    private void checkStorageLimit(String userPrefix, long uploadSize) throws Exception {
        long currentSize = getUserStorageSize(userPrefix);
        long maxSize = getMaxStorageSize();
        long availableSize = maxSize - currentSize;

        if (uploadSize > availableSize) {
            throw new StorageLimitExceededException(
                    "Storage limit exceeded: available " + SizeFormatter.formatSize(availableSize)
                            + ", uploading " + SizeFormatter.formatSize(uploadSize));
        }
    }
}
