package ru.vladshi.cloudfilestorage.service;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vladshi.cloudfilestorage.BaseTestcontainersForTest;
import ru.vladshi.cloudfilestorage.entity.User;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class MinioServiceImplTest extends BaseTestcontainersForTest {

    @Autowired
    private MinioService minioService;

    @Autowired
    private MinioClient minioClient;

    private final static User testuser = new User();

    @BeforeAll
    public static void init() {
        testuser.setUsername("testuser");
        testuser.setId(1L);
    }

//    @BeforeEach
//    public void beforeEach() {
//        cleanTestUserFolder();
//    }

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
}
