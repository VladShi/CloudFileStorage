package ru.vladshi.cloudfilestorage.storage.service;

import io.minio.PutObjectArgs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.storage.exception.StorageLimitExceededException;
import ru.vladshi.cloudfilestorage.storage.model.StorageUsageInfo;
import ru.vladshi.cloudfilestorage.storage.service.impl.MinioClientProvider;
import ru.vladshi.cloudfilestorage.storage.service.impl.MinioStorageUsageServiceImpl;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {MinioStorageUsageServiceImpl.class, MinioClientProvider.class,
                AbstractMinioServiceTest.MinioClientConfig.class},
        properties = {
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "storage.max-size-per-user=1MB"
        })
public class MinioStorageUsageServiceImplTest extends AbstractMinioServiceTest {

    private static final long MAX_STORAGE_SIZE = 1024 * 1024; // 1MB
    private static final long SMALL_FILE_SIZE = HELLO_MINIO_BYTES.length;
    private static final long LARGE_FILE_SIZE = MAX_STORAGE_SIZE + SMALL_FILE_SIZE;

    @Autowired
    private StorageUsageService storageUsageService;

    @Test
    @DisplayName("Получение информации о пустом хранилище пользователя")
    void shouldReturnZeroUsageForEmptyStorage() throws Exception {

        StorageUsageInfo info = storageUsageService.getInfo(ROOT_USER_FOLDER);

        assertEquals(0, info.currentSize(), "Текущий размер должен быть 0 для пустого хранилища");
        assertEquals(MAX_STORAGE_SIZE, info.maxSize(), "Максимальный размер должен соответствовать");
    }

    @Test
    @DisplayName("Получение информации о хранилище с одним файлом")
    void shouldReturnCorrectUsageWithOneFile() throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + TEST_FILE_NAME)
                .stream(new ByteArrayInputStream(HELLO_MINIO_BYTES), SMALL_FILE_SIZE, -1)
                .build());

        StorageUsageInfo info = storageUsageService.getInfo(ROOT_USER_FOLDER);

        assertEquals(SMALL_FILE_SIZE, info.currentSize(), "Текущий размер должен соответствовать размеру файла");
        assertEquals(MAX_STORAGE_SIZE, info.maxSize(), "Максимальный размер должен соответствовать");
    }

    @Test
    @DisplayName("Проверка лимита при загрузке файла в пределах допустимого")
    void shouldAllowUploadWithinLimit() throws Exception {
        assertDoesNotThrow(() -> storageUsageService.checkLimit(ROOT_USER_FOLDER, MULTIPART_TEST_FILE),
                "Загрузка файла в пределах лимита не должна выбросить исключение");
    }

    @Test
    @DisplayName("Проверка лимита при превышении для одного файла")
    void shouldThrowExceptionWhenSingleFileExceedsLimit() throws Exception {
        byte[] largeContent = new byte[(int) LARGE_FILE_SIZE];
        MultipartFile largeFile = new MockMultipartFile("file", TEST_FILE_NAME, "text/plain", largeContent);

        assertThrows(StorageLimitExceededException.class,
                () -> storageUsageService.checkLimit(ROOT_USER_FOLDER, largeFile),
                "Должно выбросить исключение при превышении лимита одним файлом");
    }

    @Test
    @DisplayName("Проверка лимита при загрузке массива файлов в пределах допустимого")
    void shouldAllowUploadArrayWithinLimit() throws Exception {
        MultipartFile[] files = new MultipartFile[]{MULTIPART_TEST_FILE, MULTIPART_TEST_FILE};

        assertDoesNotThrow(() -> storageUsageService.checkLimit(ROOT_USER_FOLDER, files),
                "Загрузка массива файлов в пределах лимита не должна выбросить исключение");
    }

    @Test
    @DisplayName("Проверка лимита при превышении для массива файлов")
    void shouldThrowExceptionWhenArrayExceedsLimit() throws Exception {
        byte[] mediumContent = new byte[(int) (MAX_STORAGE_SIZE / 2 + SMALL_FILE_SIZE)];
        MultipartFile mediumFile = new MockMultipartFile("file", TEST_FILE_NAME, "text/plain", mediumContent);
        MultipartFile[] files = new MultipartFile[]{mediumFile, mediumFile};

        assertThrows(StorageLimitExceededException.class,
                () -> storageUsageService.checkLimit(ROOT_USER_FOLDER, files),
                "Должно выбросить исключение при превышении лимита массивом файлов");
    }
}
