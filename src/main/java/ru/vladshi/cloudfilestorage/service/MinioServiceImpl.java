package ru.vladshi.cloudfilestorage.service;

import io.minio.*;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.vladshi.cloudfilestorage.dto.StorageItem;

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

    // создание какой-то папки по указанному пути // TODO так же добавить тесты к этому методу. То что папка создается. То что создается вложенная папка

    // удаление папки со всем вложенным

    // переименование папки с изменением пути ко всем вложенным

    // загрузка конкретного файла на сервер по пути

    // удаление конкретного файла по пути

    // переименование конкретного файла по пути

    // скачивание конкретного файла по пути

    // скачивание конкретной папки со всем вложенным по пути
}
