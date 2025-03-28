package ru.vladshi.cloudfilestorage.storage.service;

import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    void upload(String path, MultipartFile file) throws Exception;

    void delete(String path, String fileToDeleteName) throws Exception;

    void rename(String path, String oldFileName, String newFileName) throws Exception;

    InputStreamResource download(String path, String fileName) throws Exception;

    long getFileSize(String path, String fileName) throws Exception;

}
