package ru.vladshi.cloudfilestorage.storage.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;

public class MinioContainerSetup {

    private static final String MINIO_IMAGE = "minio/minio:RELEASE.2024-12-18T13-15-44Z";
    private static final String TEST_BUCKET_NAME = "test-bucket";
    private static final String TEST_USER_PREFIX = "1-test_user/";

    private MinioContainerSetup() {
    }

    public static MinIOContainer initializeWithEnvironment() {
        MinIOContainer container = new MinIOContainer(DockerImageName.parse(MINIO_IMAGE))
                .withUserName("minioadmin")
                .withPassword("minioadmin")
                .withExposedPorts(9000);
        container.start();

        MinioClient tempClient = buildTempMinioClient(container);
        try {
            ensureTestBucketExists(tempClient);
            createTestUserFolder(tempClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize test MinIO environment", e);
        }
        return container;
    }

    private static MinioClient buildTempMinioClient(MinIOContainer container) {
        return MinioClient.builder()
                .endpoint("http://" + container.getHost() + ":" + container.getMappedPort(9000))
                .credentials(container.getUserName(), container.getPassword())
                .build();
    }

    private static void ensureTestBucketExists(MinioClient client) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(TEST_BUCKET_NAME).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(TEST_BUCKET_NAME).build());
        }
    }

    private static void createTestUserFolder(MinioClient client) throws Exception {
        client.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(TEST_USER_PREFIX)
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );
    }

    public static String getTestBucketName() {
        return TEST_BUCKET_NAME;
    }

    public static String getTestUserPrefix() {
        return TEST_USER_PREFIX;
    }
}
