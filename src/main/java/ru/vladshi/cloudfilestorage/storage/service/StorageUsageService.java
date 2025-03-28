package ru.vladshi.cloudfilestorage.storage.service;

import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.storage.model.StorageUsageInfo;

public interface StorageUsageService {

    StorageUsageInfo getInfo(String userPrefix) throws Exception;

    void checkLimit(String userPrefix, MultipartFile file) throws Exception;

    void checkLimit(String userPrefix, MultipartFile[] files) throws Exception;

}
