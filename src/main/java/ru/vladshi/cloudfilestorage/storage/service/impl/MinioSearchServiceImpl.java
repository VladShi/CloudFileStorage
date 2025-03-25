package ru.vladshi.cloudfilestorage.storage.service.impl;

import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.vladshi.cloudfilestorage.storage.model.StorageItem;
import ru.vladshi.cloudfilestorage.storage.service.AbstractMinioService;
import ru.vladshi.cloudfilestorage.storage.service.SearchService;
import ru.vladshi.cloudfilestorage.storage.util.PathUtil;

import java.util.ArrayList;
import java.util.List;

@Service
public class MinioSearchServiceImpl extends AbstractMinioService implements SearchService {

    @Autowired
    public MinioSearchServiceImpl(MinioClientProvider minioClientProvider) {
        super(minioClientProvider);
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

            String itemName = PathUtil.extractNameFromPath(fullItemPath);

            if (itemName.toLowerCase().contains(query.toLowerCase())) {
                String relativePath = fullItemPath.substring(userPrefix.length());
                itemsThatMatch.add(new StorageItem(relativePath, isFolder, item.size()));
            }
        }

        return itemsThatMatch;
    }
}
