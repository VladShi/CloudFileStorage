package ru.vladshi.cloudfilestorage.service;

import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.dto.StorageItem;

import java.util.List;

public interface MinioService {

    List<StorageItem> getItems(String userPrefix, String path);

    void createFolder(String basePath, String folderPath, String newFolderName);

    void deleteFolder(String basePath, String folderPath, String folderName);

    void renameFolder(String basePath, String folderPath, String oldFolderName, String newFolderName);

    // загрузка конкретного файла на сервер по пути
    void uploadFile(String basePath, String folderPath, MultipartFile file);
}
