package ru.vladshi.cloudfilestorage.storage.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Getter
public class MinioClientProvider {
    private final String usersBucketName;
    private final MinioClient minioClient;

    public MinioClientProvider(MinioClient minioClient, @Value("${minio.bucket.users}") String usersBucketName) {
        this.minioClient = minioClient;
        this.usersBucketName = usersBucketName;
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
}
