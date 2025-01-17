package ru.vladshi.cloudfilestorage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.vladshi.cloudfilestorage.dto.StorageItem;
import ru.vladshi.cloudfilestorage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.exception.FolderNotFoundException;
import ru.vladshi.cloudfilestorage.exception.ObjectDeletionException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

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
    public List<StorageItem> getItems(String basePath, String path) {
        List<StorageItem> items = new ArrayList<>();

        String fullPrefix = basePath;
        if (path != null && !path.isBlank()) {
            fullPrefix = basePath + path;
            if (!fullPrefix.endsWith("/")) {
                fullPrefix += "/";
            }
        }

        try {
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
                String objectName = item.objectName();
                boolean isFolder = objectName.endsWith("/");

                items.add(new StorageItem(objectName, isFolder, item.size()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list user items", e);
        }

        return items;
    }

    // создание какой-то папки по указанному пути
    @Override
    public void createFolder(String basePath, String folderPath, String newFolderName) {
        if (newFolderName == null || newFolderName.isBlank()) {
            throw new IllegalArgumentException("Folder name cannot be null or empty");
        } else if (!newFolderName.endsWith("/")) {
            newFolderName += "/";
        }

        // Убедимся, что folderPath заканчивается на "/", и не пустое
        if (folderPath == null || folderPath.isBlank()) {
            folderPath = "";
        } else if (!folderPath.endsWith("/")) {
            folderPath += "/";
        }

        // Полный путь к родительской папке
        String parentPath = basePath + folderPath;

        // Полный путь к новой папке
        String fullPath = parentPath + newFolderName;

        try {
            // Проверяем, существует ли родительская папка, если она не является базовой папкой
            if (!folderPath.isEmpty() && !folderExists(parentPath)) {
                throw new FolderNotFoundException("Folder does not exist: " + parentPath);
            }

            // Проверяем, существует ли уже новая папка
            if (folderExists(fullPath)) {
                throw new FolderAlreadyExistsException("Folder already exists: " + fullPath);
            }

            // Создаем папку (пустой объект)
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullPath)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .build()
            );
        } catch (FolderNotFoundException | FolderAlreadyExistsException e) {
            // Пробрасываем кастомные исключения дальше
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create folder: " + fullPath, e);
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
    public void deleteFolder(String basePath, String folderPath, String folderName) {
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("Folder name cannot be null or empty");
        } else if (!folderName.endsWith("/")) {
            folderName += "/";
        }

        // Убедимся, что folderPath заканчивается на "/", и не пустое
        if (folderPath == null || folderPath.isBlank()) {
            folderPath = "";
        } else if (!folderPath.endsWith("/")) {
            folderPath += "/";
        }

        // Полный путь к удаляемой папке
        String fullPath = basePath + folderPath + folderName;

        try {
            // Проверяем, существует ли удаляемая папка
            if (!folderExists(fullPath)) {
                throw new FolderNotFoundException("Folder not found: " + fullPath);
            }

            // Получаем список всех объектов в папке (включая вложенные)
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
                            .prefix(fullPath)
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
                    throw new ObjectDeletionException("Failed to delete object: " + error.objectName());
                }
            }

        } catch (FolderNotFoundException | ObjectDeletionException e) {
            throw e; // Пробрасываем кастомное исключение
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete folder: " + fullPath, e);
        }
    }

    // переименование папки с изменением пути ко всем вложенным(не забыть проверки на существование, что бы не затирать существующие)
    @Override
    public void renameFolder(String basePath, String folderPath, String oldFolderName, String newFolderName) {
        if (oldFolderName == null || oldFolderName.isBlank()) {
            throw new IllegalArgumentException("Folder name cannot be null or empty");
        } else if (!oldFolderName.endsWith("/")) {
            oldFolderName += "/";
        }

        if (newFolderName == null || newFolderName.isBlank()) {
            throw new IllegalArgumentException("Folder name cannot be null or empty");
        } else if (!newFolderName.endsWith("/")) {
            newFolderName += "/";
        }

        // Убедимся, что folderPath заканчивается на "/", и не пустое
        if (folderPath == null || folderPath.isBlank()) {
            folderPath = "";
        } else if (!folderPath.endsWith("/")) {
            folderPath += "/";
        }

        // Полный путь к удаляемой старой папке
        String fullOldPath = basePath + folderPath + oldFolderName;

        // Полный путь к новой папке
        String fullNewPath = basePath + folderPath + newFolderName;

        try {
            // Проверяем, существует ли удаляемая папка
            if (!folderExists(fullOldPath)) {
                throw new FolderNotFoundException("Folder not found: " + fullOldPath);
            }

            // Проверяем, что не занято имя папки для переименования
            if (folderExists(fullNewPath)) {
                throw new FolderAlreadyExistsException("Folder already exist: " + fullNewPath);
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
                String newObjectName = oldObjectName.replaceFirst(fullOldPath, fullNewPath);

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
                    throw new ObjectDeletionException("Failed to delete object: " + error.objectName());
                }
            }

        } catch (FolderNotFoundException | ObjectDeletionException | FolderAlreadyExistsException e) {
            throw e; // Пробрасываем кастомное исключение
        } catch (Exception e) {
            throw new RuntimeException("Failed to rename folder: " + fullOldPath, e);
        }
    }

    // загрузка конкретного файла на сервер по пути

    // удаление конкретного файла по пути

    // переименование конкретного файла по пути

    // скачивание конкретного файла по пути

    // скачивание конкретной папки со всем вложенным по пути

    // загрузка папки с вложением

    // поиск по имени, части имени
}
