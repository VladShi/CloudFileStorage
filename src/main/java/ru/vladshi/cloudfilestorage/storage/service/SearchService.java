package ru.vladshi.cloudfilestorage.storage.service;

import ru.vladshi.cloudfilestorage.storage.model.StorageItem;

import java.util.List;

public interface SearchService {

    List<StorageItem> searchItems(String basePath, String query) throws Exception;

}
