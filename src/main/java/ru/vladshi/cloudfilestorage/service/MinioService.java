package ru.vladshi.cloudfilestorage.service;

import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.dto.StorageItem;

import java.util.List;

public interface MinioService {

    List<StorageItem> getItems(String userPrefix, String path);

    void createFolder(String basePath, String folderPath, String newFolderName);

    void deleteFolder(String basePath, String folderPath, String folderName);

    void renameFolder(String basePath, String folderPath, String oldFolderName, String newFolderName);

    void uploadFile(String basePath, String folderPath, MultipartFile file);

    void deleteFile(String basePath, String folderPath, String fileName);

    void renameFile(String basePath, String folderPath, String oldFileName, String newFileName);

    void uploadFolder(String basePath, String folderPath, String folderName, MultipartFile[] files);

    InputStreamResource downloadFile(String basePath, String folderPath, String fileName);
}
