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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // получение содержимого конкретной папки пользователя для отображения во вью
    @Override
    public List<StorageItem> getItems(String userPrefix, String folderPath) {
        List<StorageItem> items = new ArrayList<>();

        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        try {
            // TODO добавить проверку существования path если оно не пустое(folderExists), в случае если не существует то кидаем исключение и по нему на главную страницу
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .startAfter(fullPrefix)
                            .prefix(fullPrefix)
                            .delimiter("/")
                            .recursive(false)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                String fullItemPath = item.objectName();
                String relativePath = fullItemPath.substring(userPrefix.length());
                boolean isFolder = fullItemPath.endsWith("/");

                items.add(new StorageItem(relativePath, isFolder, item.size()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list user items", e);
        }

        return items;
    }

    @Override
    public void createFolder(String userPrefix, String folderPath, String folderName) {
        //TODO переименовать параметр folderName, после того как вынесем общую логику для составления и валидации пути
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("Folder name cannot be null or empty");
        }

        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        String fullNewFolderPath = fullPrefix + folderName + "/";

        try {
            if (!fullPrefix.equals(userPrefix + "/") && !folderExists(fullPrefix)) {
                throw new FolderNotFoundException("Folder does not exist: " + folderPath + folderName + "/");
            }

            if (folderExists(fullNewFolderPath)) {
                throw new FolderAlreadyExistsException("Folder already exists: " + folderName);
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullNewFolderPath)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1) // TODO вынести как emptyContent для ясности
                            .build()
            );
        } catch (FolderNotFoundException | FolderAlreadyExistsException e) {
            // Пробрасываем кастомные исключения дальше
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create folder: " + fullNewFolderPath, e);
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

    // удаление папки со всем вложенным
    @Override
    public void deleteFolder(String userPrefix, String folderPath, String folderName) {
        //TODO переименовать параметр folderName, после того как вынесем общую логику для составления и валидации пути
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("Folder name cannot be null or empty");
        }

        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        String folderToDeleteFullPath = fullPrefix + folderName + "/";

        try {
            // Проверяем, существует ли удаляемая папка
            if (!folderExists(folderToDeleteFullPath)) {
                throw new FolderNotFoundException("Folder not found: " + folderPath + folderName + "/");
            }

            // Получаем список всех объектов в папке (включая вложенные)
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .prefix(folderToDeleteFullPath)
                            .recursive(true)
                            .build()
            );

            // Собираем объекты для удаления
            List<DeleteObject> objectsToDelete = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                objectsToDelete.add(new DeleteObject(item.objectName()));
            }

            // Удаляем объекты пачкой
            if (!objectsToDelete.isEmpty()) {
                Iterable<Result<DeleteError>> deletingResults = minioClient.removeObjects(
                                RemoveObjectsArgs.builder()
                                .bucket(usersBucketName)
                                .objects(objectsToDelete)
                                .build()
                );

                // Проверяем ошибки
                for (Result<DeleteError> result : deletingResults) {
                    DeleteError error = result.get();
                    throw new ObjectDeletionException(
                            "Failed to delete object: " + error.objectName().substring(userPrefix.length()));
                }
            }

        } catch (FolderNotFoundException | ObjectDeletionException e) {
            throw e; // Пробрасываем кастомное исключение
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete folder: " + folderPath + folderName + "/", e);
        }
    }

    // переименование папки с изменением пути ко всем вложенным
    @Override
    public void renameFolder(String userPrefix, String folderPath, String oldFolderName, String newFolderName) {
        //TODO вынести общий метод. Общий метод будем вызывать дважды и передавать сначала старое потом новое название
        //TODO если имя совпадает со старым return после проверок null
        if (oldFolderName == null || oldFolderName.isBlank() || newFolderName == null || newFolderName.isBlank()) {
            throw new IllegalArgumentException("Folder name cannot be null or empty");
        }

        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        String fullOldPath = fullPrefix + oldFolderName + "/";

        String fullNewPath = fullPrefix + newFolderName + "/";

        try {
            // Проверяем, существует ли удаляемая папка
            if (!folderExists(fullOldPath)) {
                throw new FolderNotFoundException("Folder not found: " + oldFolderName);
            }

            // Проверяем, что не занято имя папки для переименования
            if (folderExists(fullNewPath)) {
                throw new FolderAlreadyExistsException("Folder already exist: " + newFolderName);
            }

            // Получаем список всех объектов с префиксом старой папки
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .prefix(fullOldPath)
                            .recursive(true)
                            .build()
            );

            List<DeleteObject> objectsToDelete = new ArrayList<>();

            // Переименовываем каждый объект
            for (Result<Item> result : results) {
                Item item = result.get();
                objectsToDelete.add(new DeleteObject(item.objectName()));

                String oldObjectName = item.objectName();

                // Формируем новое имя объекта
//                String newObjectName = oldObjectName.replaceFirst(fullOldPath, fullNewPath);
                String newObjectName = fullNewPath + oldObjectName.substring(fullOldPath.length());

                // Копируем объект с новым именем
                minioClient.copyObject(
                        CopyObjectArgs.builder()
                                .bucket(usersBucketName)
                                .object(newObjectName)
                                .source(CopySource.builder()
                                        .bucket(usersBucketName)
                                        .object(oldObjectName)
                                        .build())
                                .build()
                );
            }

            if (!objectsToDelete.isEmpty()) {
                Iterable<Result<DeleteError>> deletingResults = minioClient.removeObjects(
                        RemoveObjectsArgs.builder()
                                .bucket(usersBucketName)
                                .objects(objectsToDelete)
                                .build()
                );

                // Проверяем ошибки
                for (Result<DeleteError> result : deletingResults) {
                    DeleteError error = result.get();
                    throw new ObjectDeletionException(
                            "Failed to delete object: " + error.objectName().substring(userPrefix.length()));
                }
            }

        } catch (FolderNotFoundException | ObjectDeletionException | FolderAlreadyExistsException e) {
            throw e; // Пробрасываем кастомное исключение
        } catch (Exception e) {
            throw new RuntimeException("Failed to rename folder: " + folderPath + oldFolderName + "/", e);
        }
    }

    // загрузка конкретного файла на сервер по пути
    @Override
    public void uploadFile(String userPrefix, String folderPath, MultipartFile file) {
        if (file == null ||
                file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("File cannot be null or nameless");
        }

        String fileName = file.getOriginalFilename();

        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        String fullFilePath = fullPrefix + fileName;

        try {
            // Проверяем, существует ли файл с таким именем
            if (fileExists(fullFilePath)) {
                throw new FileAlreadyExistsInStorageException("File already exists: " + fileName);
            }

            File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
            file.transferTo(tempFile);

            // Загружаем файл в MinIO
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullFilePath)
                            .filename(tempFile.getAbsolutePath())
                            .build()
            );

            // Удаляем временный файл
            boolean isTempFileDeleted = tempFile.delete();
            if (!isTempFileDeleted) {
                throw new RuntimeException("Failed to delete temporary file");
            }
        } catch (FileAlreadyExistsInStorageException e) {
            throw e; // Пробрасываем кастомное исключение
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file: " + fullFilePath, e);
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

    // удаление конкретного файла по пути
    @Override
    public void deleteFile(String userPrefix, String folderPath, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        String fullFilePath = fullPrefix + fileName;

        try {
            if (!fileExists(fullFilePath)) {
                throw new FileNotFoundInStorageException("File not found: " + fileName);
            }

            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(usersBucketName).object(fullFilePath).build());

        } catch (FileNotFoundInStorageException e) {
            throw e; // Пробрасываем кастомное исключение
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file: " + fullFilePath, e);
        }
    }

    // переименование конкретного файла по пути
    @Override
    public void renameFile(String userPrefix, String folderPath, String oldFileName, String newFileName) {
        //TODO вынести общий метод. Общий метод будем вызывать дважды и передавать сначала старое потом новое название
        //TODO если имя совпадает со старым return после проверок null
        if (oldFileName == null || oldFileName.isBlank()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        if (newFileName == null || newFileName.isBlank()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        String fullOldFilePath = fullPrefix + oldFileName;

        String fullNewFilePath = fullPrefix + newFileName;

        try {
            if (!fileExists(fullOldFilePath)) {
                throw new FileNotFoundInStorageException("File not found: " + fullOldFilePath);
            }

            // Проверяем, что не занято имя файла для переименования
            if (fileExists(fullNewFilePath)) {
                throw new FileAlreadyExistsInStorageException("File already exist: " + newFileName);
            }

            // Копируем объект с новым именем
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

            // Удаляем старый файл
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(usersBucketName).object(fullOldFilePath).build());

        } catch (FileNotFoundInStorageException | FileAlreadyExistsInStorageException e) {
            throw e; // Пробрасываем кастомное исключение
        } catch (Exception e) {
            throw new RuntimeException("Failed to rename file: " + fullOldFilePath, e);
        }
    }

    // загрузка папки с вложением
    @Override
    public void uploadFolder(String userPrefix, String folderPath, String uploadedFolderName, MultipartFile[] files) {
        if (files == null || files.length == 0) { // TODO проверить что будет при загрузке пустой папки
            throw new IllegalArgumentException("Files cannot be null or empty");
        }

        if (uploadedFolderName == null || uploadedFolderName.isBlank()) {
            throw new IllegalArgumentException("Folder name cannot be null or empty");
        }

        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

        String fullUploadedFolderPath = fullPrefix + uploadedFolderName + "/";

        List<File> tempFilesToDelete = new ArrayList<>();

        try {
            // Проверяем, что папка, в которую загружается папка, существует (или это корневая папка)
            if (!fullPrefix.equals(userPrefix + "/") && !folderExists(fullPrefix)) {
                throw new FolderNotFoundException("Folder does not exist: " + folderPath);
            }

            // Проверяем, что папка с таким же именем не существует
            if (folderExists(fullUploadedFolderPath)) {
                throw new FolderAlreadyExistsException("Folder already exists: " + uploadedFolderName);
            }

            Set<String> foldersToCreate = new HashSet<>();

            foldersToCreate.add(fullUploadedFolderPath);

            List<SnowballObject> objectsToUpload = new ArrayList<>();

            for (MultipartFile file : files) {
                if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
                    throw new IllegalArgumentException("File cannot be null or empty");
                }

                // Полный путь к загружаемому файлу
                String fileFullPath = fullPrefix + file.getOriginalFilename();

                // Добавляем родительскую папку файла в список папок для создания
                String pathToFileParentFolder = fileFullPath.substring(0, fileFullPath.lastIndexOf("/") + 1);
                foldersToCreate.add(pathToFileParentFolder);

                // Извлекаем только имя файла (без пути)
                String fileName = extractNameFromPath(file.getOriginalFilename());

                // Создаем временный файл
                File tempFile = File.createTempFile("upload-", fileName);
                file.transferTo(tempFile);
                tempFilesToDelete.add(tempFile);

                // Добавляем файл к списку объектов для загрузки
                objectsToUpload.add(new SnowballObject(
                        fileFullPath,
                        tempFile.getAbsolutePath()
                ));
            }

            for (String path : foldersToCreate) {
                objectsToUpload.add(new SnowballObject(
                        path,
                        new ByteArrayInputStream(new byte[0]),
                        0,
                        null
                ));
            }

            minioClient.uploadSnowballObjects(
                    UploadSnowballObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .objects(objectsToUpload)
                            .build()
            );
        } catch (FolderNotFoundException | FolderAlreadyExistsException e) {
            throw e; // Пробрасываем кастомные исключения
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload folder: " + uploadedFolderName, e);
        } finally {
            for (File tempFile : tempFilesToDelete) {
                try {
                    boolean isDeleted = tempFile.delete();
                    if (!isDeleted) {
                        // log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    // log.error("Error deleting temporary file: {}", tempFile.getAbsolutePath(), e);
                }
            }
        }
    }

    // скачивание конкретного файла по пути
    @Override
    public InputStreamResource downloadFile(String userPrefix, String folderPath, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        String fullPrefix = userPrefix;
        if (folderPath != null && !folderPath.isBlank()) {
            fullPrefix += folderPath;
        }
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }

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
            // Проверяем, что файл не найден
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new FileNotFoundInStorageException("File not found: " +
                        (folderPath != null ? folderPath + fileName : fileName));
            }
            // Пробрасываем другие ошибки
            throw new RuntimeException("Failed to download file: " + fullFilePath, e);
        } catch (Exception e) {
            // Обрабатываем другие исключения
            throw new RuntimeException("Failed to download file: " + fullFilePath, e);
        }
    }

    // скачивание конкретной папки со всем вложенным по пути
    @Override
    public InputStreamResource downloadFolder(String basePath, String folderPath, String folderName) {
        // Формируем полный путь к папке
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("Folder relativePath cannot be null or empty");
        } else if (!folderName.endsWith("/")) {
            folderName += "/";
        }

        // Убедимся, что folderPath заканчивается на "/", и не пустое
        if (folderPath == null || folderPath.isBlank()) {
            folderPath = "";
        } else if (!folderPath.endsWith("/")) {
            folderPath += "/";
        }

        String fullFolderPath = basePath + folderPath + folderName;

        Path tempZipFile = null;

        try {

            // Проверяем, что папка, которую скачиваем, существует
            if (!folderExists(fullFolderPath)) {
                throw new FolderNotFoundException("Folder does not exist: " + fullFolderPath);
            }

            // Получаем список всех объектов в папке (рекурсивно)
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .prefix(fullFolderPath)
                            .recursive(true)
                            .build()
            );

            // Создаем временный файл для ZIP-архива
            tempZipFile = Files.createTempFile("folder-", ".zip");
            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(tempZipFile))) {
                // Добавляем каждый файл в архив
                for (Result<Item> result : results) {
                    Item item = result.get();
                    String objectName = item.objectName();
                    boolean isFolder = objectName.endsWith("/");
                    if (!isFolder) { // Пропускаем папки, так как они создаются автоматически
                        String entryName = objectName.substring(fullFolderPath.length()); // Относительный путь в архиве

                        // Добавляем файл в архив
                        zipOut.putNextEntry(new ZipEntry(entryName));
                        try (InputStream inputStream = minioClient.getObject(
                                GetObjectArgs.builder()
                                        .bucket(usersBucketName)
                                        .object(objectName)
                                        .build()
                        )) {
                            inputStream.transferTo(zipOut);
                        }
                        zipOut.closeEntry();
                    }
                }
            }

            // Возвращаем ZIP-архив в виде InputStreamResource
            return new InputStreamResource(Files.newInputStream(tempZipFile));
        } catch (FolderNotFoundException e) {
            throw e; // Пробрасываем кастомные исключения
        } catch (Exception e) {
            throw new RuntimeException("Failed to download folder: " + fullFolderPath, e);
        } finally {
            if (tempZipFile != null) {
                try {
                    Files.deleteIfExists(tempZipFile); // Удаляем временный файл
                } catch (IOException e) {
                    // Логируем ошибку удаления файла (если она произошла)
                    e.printStackTrace();
                }
            }
        }
    }

    // поиск по имени, части имени
    @Override
    public List<StorageItem> searchItems(String userPrefix, String query) {
        List<StorageItem> results = new ArrayList<>();

        try {
            // Получаем список всех объектов в "папке" пользователя (рекурсивно)
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .prefix(userPrefix)
                            .recursive(true)
                            .build()
            );

            // Фильтруем объекты по имени
            for (Result<Item> result : objects) {
                Item item = result.get();
                String fullItemPath = item.objectName();
                boolean isFolder = fullItemPath.endsWith("/");

                // Извлекаем имя файла/папки (без пути)
                String fileName = extractNameFromPath(fullItemPath);

                // Проверяем, содержит ли имя ключевое слово
                if (fileName.toLowerCase().contains(query.toLowerCase())) {
                    String relativePath = fullItemPath.substring(userPrefix.length());
                    results.add(new StorageItem(relativePath, isFolder, item.size()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to search by relativePath: " + query, e);
        }

        return results;
    }

    private String extractNameFromPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            throw new IllegalArgumentException("Object name cannot be null or empty");
        }

        // Удаляем последний слэш, если он есть
        if (fullPath.endsWith("/")) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }

        int lastSlashIndex = fullPath.lastIndexOf('/');

        if (lastSlashIndex == -1) {
            // Полный путь не содержит слэшей, это корневой файл или папка
            return "/";
        }

        // Извлекаем имя файла или папки
        return fullPath.substring(lastSlashIndex + 1);
    }
}
