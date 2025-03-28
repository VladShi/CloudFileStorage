package ru.vladshi.cloudfilestorage.storage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

@Testcontainers
public abstract class AbstractMinioServiceTest {

    protected static final String TEST_BUCKET_NAME = MinioContainerSetup.getTestBucketName();
    protected static final String ROOT_USER_FOLDER = MinioContainerSetup.getTestUserPrefix();

    protected static final String FIRST_LEVEL_FOLDER = "first-folder/";
    protected static final String TEST_FILE_NAME = "test-file.txt";
    protected static final byte[] HELLO_MINIO_BYTES = "Hello Minio".getBytes(StandardCharsets.UTF_8);
    protected static final MultipartFile MULTIPART_TEST_FILE = new MockMultipartFile(
            "file", TEST_FILE_NAME, "text/plain", HELLO_MINIO_BYTES
    );

    @Autowired
    protected MinioClient minioClient;

    private static final MinIOContainer minioContainer = MinioContainerSetup.initializeWithEnvironment();

    @TestConfiguration
    static class MinioClientConfig {
        @Bean
        public MinioClient minioClient() {
            return MinioClient.builder()
                    .endpoint("http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort(9000))
                    .credentials(minioContainer.getUserName(), minioContainer.getPassword())
                    .build();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.bucket.users", () -> TEST_BUCKET_NAME);
        registry.add("storage.max-size-per-user", () -> "1MB");
    }

    @BeforeEach
    void setUp() throws Exception {
        cleanFolder(ROOT_USER_FOLDER);
    }

    protected boolean folderExists(String folderPath) throws Exception {
        if (folderPath.endsWith("//")) {
            return false;
        }
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(TEST_BUCKET_NAME)
                            .object(folderPath)
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

    protected boolean fileExists(String filePath) throws Exception {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(TEST_BUCKET_NAME)
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

    protected void cleanFolder(String folderPath) throws Exception {
        ListObjectsArgs args = ListObjectsArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .startAfter(folderPath)
                .prefix(folderPath)
                .recursive(true)
                .build();
        Iterable<Result<Item>> objects = minioClient.listObjects(args);
        for (Result<Item> result : objects) {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(TEST_BUCKET_NAME)
                            .object(result.get().objectName())
                            .build()
            );
        }
    }
}
