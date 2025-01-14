package ru.vladshi.cloudfilestorage.service;

import io.minio.*;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vladshi.cloudfilestorage.BaseTestcontainersForTest;
import ru.vladshi.cloudfilestorage.dto.StorageItem;
import ru.vladshi.cloudfilestorage.entity.User;
import ru.vladshi.cloudfilestorage.util.UserPrefixUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class MinioServiceImplTest extends BaseTestcontainersForTest {

    @Autowired
    private MinioService minioService;

    @Autowired
    private MinioClient minioClient;

    private final static User testUser = new User();
    private static String testUserPrefix;

    @BeforeAll
    public static void init() {
        testUser.setUsername("testuser");
        testUser.setId(1L);
        testUserPrefix = UserPrefixUtil.generateUserPrefix(testUser.getUsername(), testUser.getId());
    }

    @BeforeEach
    public void beforeEach() {
        cleanTestUserFolder();
    }

//    @Test
//    void testMinioContainer() {
//        String endpoint = minioContainer.getHost();
//        String s3url = minioContainer.getS3URL();
//        int port = minioContainer.getMappedPort(9000); // Использование измененного порта
//        var port4 = minioContainer.getBoundPortNumbers();
//        var port2 = minioContainer.getExposedPorts();
//        var port3 = minioContainer.getPortBindings();
//
//        // Ваш код для тестирования MinIO
//        System.out.println("MinIO Endpoint: " + endpoint);
//        System.out.println("MinIO S3URL: " + endpoint);
//        System.out.println("MinIO Mapped Port: " + port);
//        port4.stream().map(i -> "MinIO Bound Port Numbers: " + i).forEach(System.out::println);
//        port2.stream().map(i -> "MinIO Exposed Port: " + i).forEach(System.out::println);
//        port3.stream().map(i -> "MinIO Port Bindings: " + i).forEach(System.out::println);
//    }

    @Test
    @DisplayName("Проверяем, что тестовый bucket создан при инициализации сервиса")
    void shouldReturnTrueIfTestBucketExists() throws Exception {
        assertTrue(minioClient.bucketExists(BucketExistsArgs.builder().bucket(TEST_BUCKET_NAME).build()));
    }

    @Test
    @DisplayName("Проверяем, что метод getItems возвращает пустой список, если у пользователя нет папок и файлов")
    void shouldReturnEmptyListIfUserHasNoFoldersAndFiles() {

        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .prefix(testUserPrefix)
                        .recursive(false)
                        .build()
        );

        assertFalse(results.iterator().hasNext(), "В MinIO не должно быть объектов с префиксом пользователя");

        List<StorageItem> items = minioService.getItems(testUserPrefix, null);

        assertTrue(items.isEmpty(), "Список должен быть пустым, если у пользователя нет папок и файлов");
    }

    private void cleanTestUserFolder() {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(TEST_BUCKET_NAME)
                            .prefix(testUserPrefix)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(TEST_BUCKET_NAME)
                                .object(item.objectName())
                                .build()
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean test user folder", e);
        }
    }
}
