package ru.vladshi.cloudfilestorage.storage.service;

import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.storage.model.StorageItem;
import ru.vladshi.cloudfilestorage.storage.model.UserStorageInfo;

import java.util.List;

public interface MinioService {

    void createUserRootFolder(String userFolderName) throws Exception;

    List<StorageItem> getItems(String path) throws Exception;

    void createFolder(String path, String newFolderName) throws Exception;

    void deleteFolder(String path, String folderName) throws Exception;

    void renameFolder(String path, String oldFolderName, String newFolderName) throws Exception;

    void uploadFile(String path, MultipartFile file) throws Exception;

    void deleteFile(String path, String fileName) throws Exception;

    void renameFile(String path, String oldFileName, String newFileName) throws Exception;

    void uploadFolder(String path, String folderName, MultipartFile[] files) throws Exception;

    InputStreamResource downloadFile(String path, String fileName) throws Exception;

    InputStreamResource downloadFolder(String path, String folderName) throws Exception;

    List<StorageItem> searchItems(String userPrefix, String query) throws Exception;

    long getFileSize(String path, String fileName) throws Exception;

    UserStorageInfo getUserStorageInfo(String userPrefix) throws Exception;

    void checkStorageLimit(String userPrefix, MultipartFile file) throws Exception;

    void checkStorageLimit(String userPrefix, MultipartFile[] files) throws Exception;
}
