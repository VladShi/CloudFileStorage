package ru.vladshi.cloudfilestorage.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {

    @Value("${minio.bucket.users}")
    private String usersBucketName;

    private final MinioClient minioClient;

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

    // создание какой-то папки по указанному пути

    // удаление папки со всем вложенным

    // переименование папки с изменением пути ко всем вложенным

    // загрузка конкретного файла на сервер по пути

    // удаление конкретного файла по пути

    // переименование конкретного файла по пути

    // скачивание конкретного файла по пути

    // скачивание конкретной папки со всем вложенным по пути
}
