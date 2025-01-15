package ru.vladshi.cloudfilestorage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
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
import ru.vladshi.cloudfilestorage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.exception.FolderNotFoundException;
import ru.vladshi.cloudfilestorage.util.UserPrefixUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class MinioServiceImplTest extends BaseTestcontainersForTest {

    @Autowired
    private MinioService minioService;

    @Autowired
    private MinioClient minioClient;

    private final static User testUser = new User(); // TODO нужен ли пользователь тут?
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

    @Test
    @DisplayName("Создание папки в basePath")
    void shouldCreateFolderInBasePath() {
        String folderPath = null;
        String newFolderName = "newFolder";
        minioService.createFolder(testUserPrefix, folderPath, newFolderName);

        // Проверяем, что папка создалась
        assertTrue(folderExists(testUserPrefix + newFolderName), "Папка должна быть создана в basePath");
    }

    @Test
    @DisplayName("Создание вложенной папки на два уровня")
    void shouldCreateNestedFolder() {
        // Сначала создаем промежуточную папку folder1/
        minioService.createFolder(testUserPrefix, "","folder1");

        // Затем создаем вложенную папку folder1/folder2/
        minioService.createFolder(testUserPrefix, "folder1/","folder2");

        // Проверяем, что обе папки создались
        assertTrue(folderExists(testUserPrefix + "folder1/"),
                "Промежуточная папка должна быть создана");
        assertTrue(folderExists(testUserPrefix + "folder1/folder2/"),
                "Вложенная папка должна быть создана");
    }

    @Test
    @DisplayName("Попытка создания вложенной папки в несуществующей папке")
    void shouldThrowExceptionWhenPathForNewPathDoesNotExist() {
        String nonExistentFolderPath = "folder1/";

        // Убедимся, что промежуточная папка не существует
        assertFalse(folderExists(testUserPrefix + nonExistentFolderPath),
                "Промежуточная папка не должна существовать");

        // Проверяем, что метод выбрасывает исключение
        assertThrows(FolderNotFoundException.class,
                () -> minioService.createFolder(testUserPrefix, nonExistentFolderPath, "newFolder"),
                "Должно выбрасываться исключение при попытке создать папку в несуществующей папке");
    }

    @Test
    @DisplayName("Попытка создания папки, которая уже существует")
    void shouldThrowExceptionWhenFolderAlreadyExists() {
        String folderPath = "";
        String folderName = "existingFolder";
        minioService.createFolder(testUserPrefix, folderPath, folderName);

        // Проверяем, что папка создалась
        assertTrue(folderExists(testUserPrefix + folderPath + folderName), "Папка должна быть создана");

        // Проверяем, что метод выбрасывает исключение при попытке создать существующую папку
        assertThrows(FolderAlreadyExistsException.class,
                () -> minioService.createFolder(testUserPrefix, folderPath, folderName),
                "Должно выбрасываться исключение при попытке создать существующую папку");
    }

    @Test
    @DisplayName("Попытка создания папки со значением null для имени")
    void shouldThrowExceptionWhenFolderNameIsNull() {

        String nullFolderName = null;

        assertThrows(IllegalArgumentException.class,
                () -> minioService.createFolder(testUserPrefix, null, nullFolderName),
                "Должно выбрасываться исключение при попытке создать папку со значением null для имени");
    }

    @Test
    @DisplayName("Попытка создания папки с пустым именем")
    void shouldThrowExceptionWhenFolderNameIsEmpty() {

        String emptyFolderName = "";

        assertThrows(IllegalArgumentException.class,
                () -> minioService.createFolder(testUserPrefix, null, emptyFolderName),
                "Должно выбрасываться исключение при попытке создать папку с пустым именем");
    }

    @Test
    @DisplayName("Удаление пустой папки")
    void shouldDeleteEmptyFolder() {
        String emptyFolderName = "emptyFolder";
        // Создаем папку
        minioService.createFolder(testUserPrefix, "", emptyFolderName);

        // Убедимся, что папка существует
        assertTrue(folderExists(testUserPrefix + emptyFolderName));

        // Удаляем папку
        minioService.deleteFolder(testUserPrefix, "", emptyFolderName);

        // Проверяем, что папка удалена
        assertFalse(folderExists(testUserPrefix + emptyFolderName));
    }

    @Test
    @DisplayName("Удаление папки с вложенными объектами")
    void shouldDeleteFolderWithNestedObjects() {
        String folderWithContent = "folderWithContent/";
        String nestedFolderName = "nestedFolder";

        // Создаем папку и вложенные объекты
        minioService.createFolder(testUserPrefix, "", folderWithContent);
        minioService.createFolder(testUserPrefix, folderWithContent , nestedFolderName);
        // Здесь можно добавить файлы, если нужно // TODO

        // Убедимся, что папка и вложенные объекты существуют
        assertTrue(folderExists(testUserPrefix + folderWithContent));
        assertTrue(folderExists(testUserPrefix + folderWithContent + nestedFolderName));

        // Удаляем папку
        minioService.deleteFolder(testUserPrefix, "", folderWithContent);

        // Проверяем, что папка и вложенные объекты удалены
        assertFalse(folderExists(testUserPrefix + folderWithContent));
        assertFalse(folderExists(testUserPrefix + folderWithContent + nestedFolderName));
    }

    @Test
    @DisplayName("Удаление папки с двумя уровнями вложенности")
    void shouldDeleteFolderWithTwoLevelsOfNesting() {
        // Имена папок
        String folderWithContent = "folderWithContent/";
        String nestedFolder1Name = "nestedFolder1/";
        String nestedFolder2Name = "nestedFolder2/";

        // Создаем папку и вложенные объекты
        minioService.createFolder(testUserPrefix, "", folderWithContent);
        minioService.createFolder(testUserPrefix, folderWithContent, nestedFolder1Name);
        minioService.createFolder(testUserPrefix, folderWithContent + nestedFolder1Name, nestedFolder2Name);

        // Убедимся, что папки существуют
        assertTrue(folderExists(testUserPrefix + folderWithContent));
        assertTrue(folderExists(testUserPrefix + folderWithContent + nestedFolder1Name ));
        assertTrue(folderExists(testUserPrefix + folderWithContent + nestedFolder1Name + nestedFolder2Name));

        // Удаляем основную папку
        minioService.deleteFolder(testUserPrefix, "", folderWithContent);

        // Проверяем, что все папки удалены
        assertFalse(folderExists(testUserPrefix + folderWithContent));
        assertFalse(folderExists(testUserPrefix + folderWithContent + nestedFolder1Name));
        assertFalse(folderExists(testUserPrefix + folderWithContent + nestedFolder1Name + nestedFolder2Name));
    }

    @Test
    @DisplayName("Удаление вложенной папки и проверка существования родительской папки")
    void shouldDeleteNestedFolderButKeepParentFolder() {
        // Имена папок
        String folderWithContent = "folderWithContent/";
        String nestedFolder1Name = "nestedFolder1/";
        String nestedFolder2Name = "nestedFolder2/";

        // Создаем папку и вложенные объекты
        minioService.createFolder(testUserPrefix, "", folderWithContent);
        minioService.createFolder(testUserPrefix, folderWithContent, nestedFolder1Name);
        minioService.createFolder(testUserPrefix, folderWithContent + nestedFolder1Name, nestedFolder2Name);

        // Убедимся, что папки существуют
        assertTrue(folderExists(testUserPrefix + folderWithContent));
        assertTrue(folderExists(testUserPrefix + folderWithContent + nestedFolder1Name));
        assertTrue(folderExists(testUserPrefix + folderWithContent + nestedFolder1Name + nestedFolder2Name));

        // Удаляем вложенную папку nestedFolder1
        minioService.deleteFolder(testUserPrefix, folderWithContent, nestedFolder1Name);

        // Проверяем, что nestedFolder1 и nestedFolder2 удалены
        assertFalse(folderExists(testUserPrefix + folderWithContent + nestedFolder1Name));
        assertFalse(folderExists(testUserPrefix + folderWithContent + nestedFolder1Name +nestedFolder2Name));

        // Проверяем, что родительская папка folderWithContent осталась
        assertTrue(folderExists(testUserPrefix + folderWithContent));
    }

    @Test
    @DisplayName("Попытка удаления несуществующей папки")
    void shouldThrowExceptionWhenDeletingNonExistentFolder() {
        String nonExistentFolder = "nonExistentFolder";

        // Убедимся, что папка не существует
        assertFalse(folderExists(testUserPrefix + nonExistentFolder));

        // Проверяем, что метод выбрасывает исключение
        assertThrows(FolderNotFoundException.class,
                () -> minioService.deleteFolder(testUserPrefix, "", nonExistentFolder));
    }

    private boolean folderExists(String folderPath) {
        if (folderPath != null && !folderPath.isBlank() && !folderPath.endsWith("/")) {
            folderPath = folderPath + "/";
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
            throw new RuntimeException("Failed to check if folder exists: " + folderPath, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to check if folder exists: " + folderPath, e);
        }
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
