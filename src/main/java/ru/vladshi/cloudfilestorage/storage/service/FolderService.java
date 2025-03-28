package ru.vladshi.cloudfilestorage.storage.service;

import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.storage.model.StorageItem;

import java.util.List;

public interface FolderService {

    void createUserRootFolder(String userFolderName) throws Exception;

    List<StorageItem> getFolderContents(String path) throws Exception;

    void create(String path, String newFolderName) throws Exception;

    void delete(String path, String folderName) throws Exception;

    void rename(String path, String oldFolderName, String newFolderName) throws Exception;

    void upload(String path, String folderToUploadName, MultipartFile[] files) throws Exception;

    InputStreamResource download(String path, String folderName) throws Exception;

}
