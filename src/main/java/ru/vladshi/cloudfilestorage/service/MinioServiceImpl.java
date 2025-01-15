package ru.vladshi.cloudfilestorage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.vladshi.cloudfilestorage.dto.StorageItem;
import ru.vladshi.cloudfilestorage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.exception.FolderNotFoundException;

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
        }

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(usersBucketName)
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
            throw new IllegalArgumentException("New folder name cannot be null or empty");
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
        String fullPath = parentPath + newFolderName + "/";

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

    // переименование папки с изменением пути ко всем вложенным

    // загрузка конкретного файла на сервер по пути

    // удаление конкретного файла по пути

    // переименование конкретного файла по пути

    // скачивание конкретного файла по пути

    // скачивание конкретной папки со всем вложенным по пути
}
