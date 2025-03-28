package ru.vladshi.cloudfilestorage.storage.service.impl;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.storage.exception.FileAlreadyExistsInStorageException;
import ru.vladshi.cloudfilestorage.storage.exception.FileNotFoundInStorageException;
import ru.vladshi.cloudfilestorage.storage.exception.FileUploadingException;
import ru.vladshi.cloudfilestorage.storage.service.AbstractMinioService;
import ru.vladshi.cloudfilestorage.storage.service.FileService;
import ru.vladshi.cloudfilestorage.storage.util.PathUtil;
import ru.vladshi.cloudfilestorage.storage.validation.StorageItemNameValidator;

import java.io.InputStream;

@Service
public class MinioFileServiceImpl extends AbstractMinioService implements FileService {

    @Autowired
    public MinioFileServiceImpl(MinioClientProvider minioClientProvider) {
        super(minioClientProvider);
    }

    @Override
    public void upload(String path, MultipartFile file) throws Exception {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new FileUploadingException("File cannot be null or nameless. Choose a file to upload.");
        }

        String fileName = file.getOriginalFilename();

        String fullFilePath = path + fileName;

        checkFileNotExists(fullFilePath);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullFilePath)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .build()
        );
    }

    @Override
    public void delete(String path, String fileToDeleteName) throws Exception {
        StorageItemNameValidator.validate(fileToDeleteName);

        String fullFilePath = path + fileToDeleteName;

        checkFileExists(fullFilePath);

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullFilePath).build()
        );
    }

    @Override
    public void rename(String path, String oldFileName, String newFileName) throws Exception {
        StorageItemNameValidator.validate(oldFileName);
        StorageItemNameValidator.validate(newFileName);

        if (oldFileName.equals(newFileName)) {
            return;
        }

        String fullOldFilePath = path + oldFileName;
        String fullNewFilePath = path + newFileName;

        checkFileExists(fullOldFilePath);
        checkFileNotExists(fullNewFilePath);

        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullNewFilePath)
                        .source(CopySource.builder()
                                .bucket(usersBucketName)
                                .object(fullOldFilePath)
                                .build())
                        .build()
        );

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullOldFilePath)
                        .build());
    }

    @Override
    public InputStreamResource download(String path, String fileName) throws Exception {
        String fullFilePath = path + fileName;

        try {
            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(fullFilePath)
                            .build()
            );

            return new InputStreamResource(inputStream);

        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                throw new FileNotFoundInStorageException(fileName);
            }
            throw e;
        }
    }

    @Override
    public long getFileSize(String path, String fileName) throws Exception {
        String fullFilePath = path + fileName;
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(usersBucketName)
                        .object(fullFilePath)
                        .build()
        );
        return stat.size();
    }

    private void checkFileExists(String fullFilePath) throws Exception {
        if (!fileExists(fullFilePath)) {
            throw new FileNotFoundInStorageException(PathUtil.extractNameFromPath(fullFilePath));
        }
    }

    private void checkFileNotExists(String fullFilePath) throws Exception {
        if (fileExists(fullFilePath)) {
            throw new FileAlreadyExistsInStorageException(PathUtil.extractNameFromPath(fullFilePath));
        }
    }

    private boolean fileExists(String filePath) throws Exception {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(usersBucketName)
                            .object(filePath)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            throw e;
        }
    }
}
