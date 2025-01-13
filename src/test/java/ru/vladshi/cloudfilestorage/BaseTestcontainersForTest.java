package ru.vladshi.cloudfilestorage;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
public abstract class BaseTestcontainersForTest {

    private static final String MYSQL_IMAGE = "mysql:9.1.0";
    private static final String MINIO_IMAGE = "minio/minio:RELEASE.2024-12-18T13-15-44Z";

    protected final static String TEST_BUCKET_NAME = "test-bucket";

//    @Container
    protected static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
				.withReuse(true) // убрать после создания минио-сервиса, вернуть управление @Container
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withCreateContainerCmdModifier(
                        cmd -> cmd.withName("mysql-test"));

//    @Container
    protected static final MinIOContainer minioContainer = new MinIOContainer(DockerImageName.parse(MINIO_IMAGE))
            .withReuse(true) // убрать после создания минио-сервиса, вернуть управление @Container
            .withUserName("minioadmin")
            .withPassword("minioadmin")
            .withExposedPorts(9000)
            .withCreateContainerCmdModifier(
                    cmd -> cmd.withName("minio-test"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {

        mysqlContainer.start(); // убрать после создания минио-сервиса, вернуть управление @Container

        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);

        minioContainer.start(); // убрать после создания минио-сервиса, вернуть управление @Container

        String endpoint = "http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort(9000);
        registry.add("minio.endpoint", () -> endpoint);
        registry.add("minio.accessKey", minioContainer::getUserName);
        registry.add("minio.secretKey", minioContainer::getPassword);
        registry.add("minio.bucket.users", () -> TEST_BUCKET_NAME);
    }
}
