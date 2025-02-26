package ru.vladshi.cloudfilestorage.service;

import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.dto.StorageItem;

import java.util.List;

public interface MinioService {

    List<StorageItem> getItems(String userPrefix, String path) throws Exception;

    void createFolder(String basePath, String folderPath, String newFolderName) throws Exception;

    void deleteFolder(String basePath, String folderPath, String folderName) throws Exception;

    void renameFolder(String basePath, String folderPath, String oldFolderName, String newFolderName) throws Exception;

    void uploadFile(String basePath, String folderPath, MultipartFile file) throws Exception;

    void deleteFile(String basePath, String folderPath, String fileName) throws Exception;

    void renameFile(String basePath, String folderPath, String oldFileName, String newFileName) throws Exception;

    void uploadFolder(String basePath, String folderPath, String folderName, MultipartFile[] files) throws Exception;

    InputStreamResource downloadFile(String basePath, String folderPath, String fileName) throws Exception;

    InputStreamResource downloadFolder(String basePath, String folderPath, String folderName) throws Exception;

    List<StorageItem> searchItems(String userPrefix, String query) throws Exception;
}
