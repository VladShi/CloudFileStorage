package ru.vladshi.cloudfilestorage.service;

import ru.vladshi.cloudfilestorage.dto.StorageItem;

import java.util.List;

public interface MinioService {

    // получение содержимого конкретной папки пользователя для отображения во вью
    List<StorageItem> getItems(String userPrefix, String path);
}
