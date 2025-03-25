package ru.vladshi.cloudfilestorage.storage.service;

import io.minio.MinioClient;
import ru.vladshi.cloudfilestorage.storage.service.impl.MinioClientProvider;

public abstract class AbstractMinioService {
    protected final MinioClient minioClient;
    protected final String usersBucketName;

    public AbstractMinioService(MinioClientProvider minioClientProvider) {
        this.minioClient = minioClientProvider.getMinioClient();
        this.usersBucketName = minioClientProvider.getUsersBucketName();
    }
}
