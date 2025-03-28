package ru.vladshi.cloudfilestorage.storage.service;

import io.minio.PutObjectArgs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.vladshi.cloudfilestorage.storage.model.StorageItem;
import ru.vladshi.cloudfilestorage.storage.service.impl.MinioClientProvider;
import ru.vladshi.cloudfilestorage.storage.service.impl.MinioSearchServiceImpl;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {MinioSearchServiceImpl.class, MinioClientProvider.class,
                AbstractMinioServiceTest.MinioClientConfig.class},
        properties = {
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        })
public class MinioSearchServiceImplTest extends AbstractMinioServiceTest {

    private static final String TEST_FOLDER_NAME = "test-folder/";

    @Autowired
    private SearchService searchService;

    @Test
    @DisplayName("Поиск с пустым запросом возвращает пустой список")
    void shouldReturnEmptyListForEmptyQuery() throws Exception {

        List<StorageItem> results = searchService.searchItems(ROOT_USER_FOLDER, "");

        assertTrue(results.isEmpty(), "Список должен быть пустым для пустого запроса");
    }

    @Test
    @DisplayName("Поиск файла по части имени")
    void shouldFindFileByPartialName() throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + TEST_FILE_NAME)
                .stream(new ByteArrayInputStream(HELLO_MINIO_BYTES), HELLO_MINIO_BYTES.length, -1)
                .build());

        List<StorageItem> results = searchService.searchItems(ROOT_USER_FOLDER, "test");

        assertEquals(1, results.size(), "Должен найти один файл");
        assertEquals(TEST_FILE_NAME, results.getFirst().relativePath(), "Найденный путь должен быть относительным");
        assertFalse(results.getFirst().isFolder(), "Элемент должен быть файлом");
    }

    @Test
    @DisplayName("Поиск папки и файла по общему запросу")
    void shouldFindFolderAndFileByCommonQuery() throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + TEST_FOLDER_NAME)
                .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                .build());
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + TEST_FILE_NAME)
                .stream(new ByteArrayInputStream(HELLO_MINIO_BYTES), HELLO_MINIO_BYTES.length, -1)
                .build());

        List<StorageItem> results = searchService.searchItems(ROOT_USER_FOLDER, "test");

        assertEquals(2, results.size(), "Должно найти папку и файл");
        assertTrue(results.stream().anyMatch(item -> item.relativePath().equals(TEST_FOLDER_NAME) && item.isFolder()),
                "Должна быть найдена папка");
        assertTrue(results.stream().anyMatch(item -> item.relativePath().equals(TEST_FILE_NAME) && !item.isFolder()),
                "Должен быть найден файл");
    }

    @Test
    @DisplayName("Поиск не включает корневую папку")
    void shouldNotIncludeRootFolderInResults() throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + TEST_FILE_NAME)
                .stream(new ByteArrayInputStream(HELLO_MINIO_BYTES), HELLO_MINIO_BYTES.length, -1)
                .build());

        List<StorageItem> results = searchService.searchItems(ROOT_USER_FOLDER, "test");

        assertEquals(1, results.size(), "Должен найти только файл, а не корневую папку");
        assertEquals(TEST_FILE_NAME, results.getFirst().relativePath(),
                "Найденный путь должен быть относительным к файлу");
    }

    @Test
    @DisplayName("Поиск ограничен папкой одного пользователя")
    void shouldSearchOnlyWithinOneUserFolder() throws Exception {
        String user1Folder = "1-test_user/";
        String user2Folder = "2-test_user/";
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(user1Folder + TEST_FILE_NAME)
                .stream(new ByteArrayInputStream(HELLO_MINIO_BYTES), HELLO_MINIO_BYTES.length, -1)
                .build());
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(user2Folder + TEST_FILE_NAME)
                .stream(new ByteArrayInputStream(HELLO_MINIO_BYTES), HELLO_MINIO_BYTES.length, -1)
                .build());

        List<StorageItem> results = searchService.searchItems(user1Folder, "test");

        assertEquals(1, results.size(), "Должен найти только один файл в папке первого пользователя");
        assertEquals(TEST_FILE_NAME, results.getFirst().relativePath(),
                "Найденный путь должен быть относительным к первому пользователю");
        assertFalse(results.stream().anyMatch(item -> item.relativePath().contains(user2Folder)),
                "Файлы второго пользователя не должны попасть в результаты");
    }
}
