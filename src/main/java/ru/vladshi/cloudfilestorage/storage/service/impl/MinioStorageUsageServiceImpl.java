package ru.vladshi.cloudfilestorage.storage.service.impl;

import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.storage.exception.StorageLimitExceededException;
import ru.vladshi.cloudfilestorage.storage.model.StorageUsageInfo;
import ru.vladshi.cloudfilestorage.storage.service.AbstractMinioService;
import ru.vladshi.cloudfilestorage.storage.service.StorageUsageService;
import ru.vladshi.cloudfilestorage.storage.util.SizeFormatter;

import java.util.Arrays;

@Service
public class MinioStorageUsageServiceImpl extends AbstractMinioService implements StorageUsageService {

    private final String maxSizePerUser;

    @Autowired
    public MinioStorageUsageServiceImpl(MinioClientProvider minioClientProvider,
                                        @Value("${storage.max-size-per-user:40MB}") String maxSizePerUser) {
        super(minioClientProvider);
        this.maxSizePerUser = maxSizePerUser;
    }

    @Override
    public StorageUsageInfo getInfo(String userPrefix) throws Exception {
        long currentSize = getUserStorageSize(userPrefix);
        long maxSize = getMaxStorageSize();
        return new StorageUsageInfo(currentSize, maxSize);
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
    public void checkLimit(String userPrefix, MultipartFile file) throws Exception {
        long uploadSize = file.getSize();
        checkStorageLimit(userPrefix, uploadSize);
    }

    @Override
    public void checkLimit(String userPrefix, MultipartFile[] files) throws Exception {
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
