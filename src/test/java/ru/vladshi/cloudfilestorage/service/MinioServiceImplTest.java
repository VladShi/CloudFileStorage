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
import org.springframework.core.io.InputStreamResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vladshi.cloudfilestorage.BaseTestcontainersForTest;
import ru.vladshi.cloudfilestorage.dto.StorageItem;
import ru.vladshi.cloudfilestorage.entity.User;
import ru.vladshi.cloudfilestorage.exception.FileAlreadyExistsInStorageException;
import ru.vladshi.cloudfilestorage.exception.FileNotFoundInStorageException;
import ru.vladshi.cloudfilestorage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.exception.FolderNotFoundException;
import ru.vladshi.cloudfilestorage.util.UserPrefixUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    @DisplayName("Проверяем, что метод getItems возвращает пустой список, если в папке нет папок и файлов")
    void shouldReturnEmptyListIfFolderHasNoFoldersAndFiles() {
        String folderPath = null;
        String requestedFolderName = "newFolder/";
        minioService.createFolder(testUserPrefix, folderPath, requestedFolderName);

        // Проверяем, что папка создалась
        assertTrue(folderExists(testUserPrefix + requestedFolderName), "Папка должна быть создана в basePath");

        List<StorageItem> items = minioService.getItems(testUserPrefix, requestedFolderName);

        assertTrue(items.isEmpty(), "Список должен быть пустым, если в папке нет папок и файлов");
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
    @DisplayName("Проверяем, что метод getItems возвращает вложенную папку, если в папке есть одна вложенная папка")
    void shouldReturnListWithInnerFolderIfFolderHasInnerFolder() {
        String folderPath = null;
        String requestedFolderName = "someFolder/";
        String innerFolderName = "innerFolder/";
        minioService.createFolder(testUserPrefix, folderPath, requestedFolderName);
        minioService.createFolder(testUserPrefix, requestedFolderName, innerFolderName);

        // Проверяем, что папка создалась
        assertTrue(folderExists(testUserPrefix + requestedFolderName), "Папка должна быть создана в basePath");
        assertTrue(folderExists(testUserPrefix + requestedFolderName + innerFolderName),
                "В запрашиваемой папке должна быть создана внутренняя папка");

        List<StorageItem> items = minioService.getItems(testUserPrefix, requestedFolderName);

        assertFalse(items.isEmpty(), "Список не должен быть пустым");
        assertEquals(1, items.size(), "Размер списка элементов должен быть равен 1");
        assertEquals(testUserPrefix + requestedFolderName + innerFolderName, items.getFirst().name(),
                "Должны получить внутреннюю папку");
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

    @Test
    @DisplayName("Переименование папки с использованием пустого пути")
    void shouldRenameFolderWithEmptyPath() {
        String oldFolderName = "folder1/";
        String renamedFolderName = "renamedFolder/";

        // Создаем папку для переименования
        minioService.createFolder(testUserPrefix, "", oldFolderName);

        // Переименовываем папку
        minioService.renameFolder(testUserPrefix, "", oldFolderName, renamedFolderName);

        // Проверяем, что старая папка удалена
        assertFalse(folderExists(testUserPrefix + oldFolderName));

        // Проверяем, что новая папка создана
        assertTrue(folderExists(testUserPrefix + renamedFolderName));
    }

    @Test
    @DisplayName("Переименование пустой папки")
    void shouldRenameEmptyFolder() {
        String parentFolder = "parentFolder/";
        String oldFolderName = "emptyFolder/";
        String renamedFolderName = "renamedEmptyFolder/";

        // Создаем родительскую папку и пустую папку
        minioService.createFolder(testUserPrefix, "", parentFolder);
        minioService.createFolder(testUserPrefix, parentFolder, oldFolderName);

        // Переименовываем папку
        minioService.renameFolder(testUserPrefix, parentFolder, oldFolderName, renamedFolderName);

        // Проверяем, что старая папка удалена
        assertFalse(folderExists(testUserPrefix + parentFolder + oldFolderName));

        // Проверяем, что новая папка создана
        assertTrue(folderExists(testUserPrefix + parentFolder + renamedFolderName));
    }

    @Test
    @DisplayName("Попытка переименования несуществующей папки")
    void shouldThrowExceptionWhenRenamingNonExistentFolder() {
        String parentFolder = "parentFolder/";
        String nonExistentFolder = "nonExistentFolder/";
        String renamedFolderName = "renamedFolder/";

        // Создаем родительскую папку
        minioService.createFolder(testUserPrefix, "", parentFolder);

        // Проверяем, что метод выбрасывает исключение
        assertThrows(FolderNotFoundException.class,
                () -> minioService.renameFolder(testUserPrefix, parentFolder, nonExistentFolder, renamedFolderName));
    }

    @Test
    @DisplayName("Попытка переименования папки в уже существующую папку")
    void shouldThrowExceptionWhenRenamingToExistingFolder() {
        String parentFolder = "parentFolder/";
        String oldFolderName = "folder1/";
        String existingFolderName = "existingFolder/";

        // Создаем родительскую папку и две вложенные папки
        minioService.createFolder(testUserPrefix, "", parentFolder);
        minioService.createFolder(testUserPrefix, parentFolder, oldFolderName);
        minioService.createFolder(testUserPrefix, parentFolder, existingFolderName);

        // Проверяем, что метод выбрасывает исключение
        assertThrows(FolderAlreadyExistsException.class,
                () -> minioService.renameFolder(testUserPrefix, parentFolder, oldFolderName, existingFolderName));
    }

    @Test
    @DisplayName("Переименование папки с вложенными папками")
    void shouldRenameFolderWithNestedFolders() {
        String folderWithContent = "folderWithContent/";
        String oldFolderName = "folder1/";
        String renamedFolderName = "renamed-folder/";

        // Создаем папку и вложенные объекты
        minioService.createFolder(testUserPrefix, "", folderWithContent);
        minioService.createFolder(testUserPrefix, folderWithContent, oldFolderName);
        minioService.createFolder(testUserPrefix, folderWithContent + oldFolderName, "nestedFolder");
        // minioService.uploadFile(); // TODO добавить файлы

        // Переименовываем папку
        minioService.renameFolder(testUserPrefix, folderWithContent, oldFolderName, renamedFolderName);

        // Проверяем, что старые объекты удалены
        assertFalse(folderExists(testUserPrefix + folderWithContent + oldFolderName));
        assertFalse(folderExists(testUserPrefix + folderWithContent + oldFolderName + "nestedFolder/"));
        // assertFalse(fileExists();

        // Проверяем, что новые объекты созданы
        assertTrue(folderExists(testUserPrefix + folderWithContent + renamedFolderName));
        assertTrue(folderExists(testUserPrefix + folderWithContent + renamedFolderName + "nestedFolder/"));
        // assertTrue(fileExists();
    }

    @Test
    @DisplayName("Загрузка файла в MinIO")
    void shouldUploadFileToBaseFolder() throws IOException {
        // Создаем тестовый файл
        String fileName = "test-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Загружаем файл
        minioService.uploadFile(testUserPrefix, "", multipartFile);

        // Проверяем, что файл загружен
        String fullPath = testUserPrefix + fileName;
        try {
            assertTrue(fileExists(fullPath), "Файл должен быть загружен в MinIO");
        } finally {
            // Удаляем временный файл
            Files.delete(tempFilePath);
        }
    }

    @Test
    @DisplayName("Загрузка файла во вложенную папку")
    void shouldUploadFileToNestedFolder() throws IOException {
        // Создаем тестовый файл
        String fileName = "test-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Создаем вложенную папку
        String nestedFolder = "nested-folder/";
        minioService.createFolder(testUserPrefix, "", nestedFolder);

        // Загружаем файл во вложенную папку
        minioService.uploadFile(testUserPrefix, nestedFolder, multipartFile);

        // Проверяем, что файл загружен
        String fullPath = testUserPrefix + nestedFolder + fileName;
        try {
            assertTrue(fileExists(fullPath), "Файл должен быть загружен в MinIO");
        } finally {
            // Удаляем временный файл
            Files.delete(tempFilePath);
        }
    }

    @Test
    @DisplayName("Попытка загрузки файла, который уже существует")
    void shouldThrowExceptionWhenUploadingExistingFile() throws IOException {
        // Создаем тестовый файл
        String fileName = "test-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Загружаем файл в первый раз
        minioService.uploadFile(testUserPrefix, "", multipartFile);

        // Проверяем, что файл загружен
        String fullPath = testUserPrefix + fileName;
        assertTrue(fileExists(fullPath), "Файл должен быть загружен в MinIO");

        // Пытаемся загрузить файл с тем же именем
        assertThrows(FileAlreadyExistsInStorageException.class,
                () -> minioService.uploadFile(testUserPrefix, "", multipartFile),
                "Должно выбрасываться исключение при попытке загрузить существующий файл");

        // Удаляем временный файл
        Files.delete(tempFilePath);
    }

    @Test
    @DisplayName("Попытка загрузки файла с пустым именем")
    void shouldThrowExceptionWhenUploadingFileWithEmptyName() throws IOException {
        // Создаем тестовый файл с пустым именем
        String fileName = "";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Пытаемся загрузить файл с пустым именем
        assertThrows(IllegalArgumentException.class,
                () -> minioService.uploadFile(testUserPrefix, "", multipartFile),
                "Должно выбрасываться исключение при попытке загрузить файл с пустым именем");

        // Удаляем временный файл
        Files.delete(tempFilePath);
    }

    @Test
    @DisplayName("Попытка загрузки файла с некорректным именем")
    void shouldThrowExceptionWhenUploadingFileWithInvalidName() throws IOException {
        // Создаем тестовый файл с некорректным именем
        String fileName = "../invalid-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Пытаемся загрузить файл с некорректным именем
        Exception exception = assertThrows(RuntimeException.class,
                () -> minioService.uploadFile(testUserPrefix, "", multipartFile),
                "Должно выбрасываться исключение при попытке загрузить файл с некорректным именем");

        // Проверяем, что причина исключения - IllegalArgumentException
        assertInstanceOf(IllegalArgumentException.class, exception.getCause(),
                "Причина исключения должна быть IllegalArgumentException");

        // Удаляем временный файл
        Files.delete(tempFilePath);
    }

    @Test
    @DisplayName("Удаление существующего файла")
    void shouldDeleteExistingFile() throws IOException {
        // Создаем тестовый файл
        String fileName = "test-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Загружаем файл
        minioService.uploadFile(testUserPrefix, "", multipartFile);

        // Проверяем, что файл загружен
        String fullPath = testUserPrefix + fileName;
        assertTrue(fileExists(fullPath), "Файл должен быть загружен в MinIO");

        // Удаляем файл
        minioService.deleteFile(testUserPrefix, "", fileName);

        // Проверяем, что файл удален
        assertFalse(fileExists(fullPath), "Файл должен быть удален из MinIO");

        // Удаляем временный файл
        Files.delete(tempFilePath);
    }

    @Test
    @DisplayName("Попытка удаления несуществующего файла")
    void shouldThrowExceptionWhenDeletingNonExistentFile() {
        String nonExistentFileName = "non-existent-file.txt";

        // Проверяем, что файл не существует
        assertFalse(fileExists(testUserPrefix + nonExistentFileName), "Файл не должен существовать в MinIO");

        // Пытаемся удалить несуществующий файл
        assertThrows(FileNotFoundInStorageException.class,
                () -> minioService.deleteFile(testUserPrefix, "", nonExistentFileName),
                "Должно выбрасываться исключение при попытке удалить несуществующий файл");
    }

    @Test
    @DisplayName("Попытка удаления файла с пустым именем")
    void shouldThrowExceptionWhenDeletingFileWithEmptyName() {
        String emptyFileName = "";

        // Пытаемся удалить файл с пустым именем
        assertThrows(IllegalArgumentException.class,
                () -> minioService.deleteFile(testUserPrefix, "", emptyFileName),
                "Должно выбрасываться исключение при попытке удалить файл с пустым именем");
    }

    @Test
    @DisplayName("Удаление файла из вложенной папки")
    void shouldDeleteFileFromNestedFolder() throws IOException {
        // Создаем тестовый файл
        String fileName = "test-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Создаем вложенную папку
        String nestedFolder = "nested-folder/";
        minioService.createFolder(testUserPrefix, "", nestedFolder);

        // Загружаем файл во вложенную папку
        minioService.uploadFile(testUserPrefix, nestedFolder, multipartFile);

        // Проверяем, что файл загружен
        String fullPath = testUserPrefix + nestedFolder + fileName;
        assertTrue(fileExists(fullPath), "Файл должен быть загружен в MinIO");

        // Удаляем файл
        minioService.deleteFile(testUserPrefix, nestedFolder, fileName);

        // Проверяем, что файл удален
        assertFalse(fileExists(fullPath), "Файл должен быть удален из MinIO");

        // Удаляем временный файл
        Files.delete(tempFilePath);
    }

    @Test
    @DisplayName("Попытка удаления файла с некорректным именем")
    void shouldThrowExceptionWhenDeletingFileWithInvalidName() {
        String invalidFileName = "../invalid-file.txt";

        // Пытаемся удалить файл с некорректным именем
        Exception exception = assertThrows(RuntimeException.class,
                () -> minioService.deleteFile(testUserPrefix, "", invalidFileName),
                "Должно выбрасываться исключение при попытке удалить файл с некорректным именем");

        // Проверяем, что причина исключения - IllegalArgumentException
        assertInstanceOf(IllegalArgumentException.class, exception.getCause(),
                "Причина исключения должна быть IllegalArgumentException");
    }

    @Test
    @DisplayName("Переименование существующего файла")
    void shouldRenameExistingFile() throws IOException {
        // Создаем тестовый файл
        String oldFileName = "test-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                oldFileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Загружаем файл
        minioService.uploadFile(testUserPrefix, "", multipartFile);

        // Проверяем, что файл загружен
        String fullOldPath = testUserPrefix + oldFileName;
        assertTrue(fileExists(fullOldPath), "Файл должен быть загружен в MinIO");

        // Новое имя файла
        String newFileName = "renamed-file.txt";

        // Переименовываем файл
        minioService.renameFile(testUserPrefix, "", oldFileName, newFileName);

        // Проверяем, что файл переименован
        String fullNewPath = testUserPrefix + newFileName;
        assertTrue(fileExists(fullNewPath), "Файл должен быть переименован");
        assertFalse(fileExists(fullOldPath), "Старый файл должен быть удален");

        // Удаляем временный файл
        Files.delete(tempFilePath);
    }

    @Test
    @DisplayName("Попытка переименования несуществующего файла")
    void shouldThrowExceptionWhenRenamingNonExistentFile() {
        String nonExistentFileName = "non-existent-file.txt";
        String newFileName = "renamed-file.txt";

        // Проверяем, что файл не существует
        assertFalse(fileExists(testUserPrefix + nonExistentFileName), "Файл не должен существовать в MinIO");

        // Пытаемся переименовать несуществующий файл
        assertThrows(FileNotFoundInStorageException.class,
                () -> minioService.renameFile(testUserPrefix, "", nonExistentFileName, newFileName),
                "Должно выбрасываться исключение при попытке переименовать несуществующий файл");
    }

    @Test
    @DisplayName("Попытка переименования файла в уже существующий файл")
    void shouldThrowExceptionWhenRenamingToExistingFile() throws IOException {
        // Создаем тестовые файлы
        String oldFileName = "test-file.txt";
        String existingFileName = "existing-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                oldFileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Загружаем файлы
        minioService.uploadFile(testUserPrefix, "", multipartFile);
        minioService.uploadFile(testUserPrefix, "", new MockMultipartFile(
                "file",
                existingFileName,
                "text/plain",
                "Hello, MinIO!".getBytes()
        ));

        // Проверяем, что файлы загружены
        assertTrue(fileExists(testUserPrefix + oldFileName), "Файл должен быть загружен в MinIO");
        assertTrue(fileExists(testUserPrefix + existingFileName), "Файл должен быть загружен в MinIO");

        // Пытаемся переименовать файл в уже существующий файл
        assertThrows(FileAlreadyExistsInStorageException.class,
                () -> minioService.renameFile(testUserPrefix, "", oldFileName, existingFileName),
                "Должно выбрасываться исключение при попытке переименовать файл в уже существующий файл");

        // Удаляем временный файл
        Files.delete(tempFilePath);
    }

    @Test
    @DisplayName("Попытка переименования файла с пустым именем")
    void shouldThrowExceptionWhenRenamingFileWithEmptyName() {
        String emptyFileName = "";

        // Пытаемся переименовать файл с пустым именем
        assertThrows(IllegalArgumentException.class,
                () -> minioService.renameFile(testUserPrefix, "", emptyFileName, "new-file.txt"),
                "Должно выбрасываться исключение при попытке переименовать файл с пустым именем");
    }

    @Test
    @DisplayName("Попытка переименования файла с пустым новым именем")
    void shouldThrowExceptionWhenRenamingFileWithEmptyNewName() throws IOException {
        // Создаем тестовый файл
        String oldFileName = "test-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                oldFileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Загружаем файл
        minioService.uploadFile(testUserPrefix, "", multipartFile);

        // Проверяем, что файл загружен
        String fullOldPath = testUserPrefix + oldFileName;
        assertTrue(fileExists(fullOldPath), "Файл должен быть загружен в MinIO");

        // Новое имя файла (пустое)
        String newFileName = "";

        // Пытаемся переименовать файл с пустым новым именем
        assertThrows(IllegalArgumentException.class,
                () -> minioService.renameFile(testUserPrefix, "", oldFileName, newFileName),
                "Должно выбрасываться исключение при попытке переименовать файл с пустым новым именем");

        // Удаляем временный файл
        Files.delete(tempFilePath);
    }

    @Test
    @DisplayName("Переименование файла во вложенной папке")
    void shouldRenameFileInNestedFolder() throws IOException {
        // Создаем тестовый файл
        String oldFileName = "test-file.txt";
        Path tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                oldFileName,
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );

        // Создаем вложенную папку
        String nestedFolder = "nested-folder/";
        minioService.createFolder(testUserPrefix, "", nestedFolder);

        // Загружаем файл во вложенную папку
        minioService.uploadFile(testUserPrefix, nestedFolder, multipartFile);

        // Проверяем, что файл загружен
        String fullOldPath = testUserPrefix + nestedFolder + oldFileName;
        assertTrue(fileExists(fullOldPath), "Файл должен быть загружен в MinIO");

        // Новое имя файла
        String newFileName = "renamed-file.txt";

        // Переименовываем файл
        minioService.renameFile(testUserPrefix, nestedFolder, oldFileName, newFileName);

        // Проверяем, что файл переименован
        String fullNewPath = testUserPrefix + nestedFolder + newFileName;
        assertTrue(fileExists(fullNewPath), "Файл должен быть переименован");
        assertFalse(fileExists(fullOldPath), "Старый файл должен быть удален");

        // Удаляем временный файл
        Files.delete(tempFilePath);
    }

    @Test
    @DisplayName("Загрузка папки в корневую папку пользователя")
    void shouldUploadFolderToRoot() throws IOException {
        // Создаем тестовые файлы
        String folderName = "my-folder/";
        String file1Name = "file1.txt";
        String subFolderName = "sub-folder/";
        String file2Name = "sub-folder/file2.txt";

        // Создаем временные файлы
        Path tempFile1 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile1, "Hello, file1!".getBytes());

        Path tempFile2 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile2, "Hello, file2!".getBytes());

        // Создаем MultipartFile из временных файлов
        MultipartFile multipartFile1 = new MockMultipartFile(
                "file",
                file1Name,
                "text/plain",
                Files.readAllBytes(tempFile1)
        );

        MultipartFile multipartFile2 = new MockMultipartFile(
                "file",
                file2Name,
                "text/plain",
                Files.readAllBytes(tempFile2)
        );

        // Загружаем папку в корневую папку пользователя
        minioService.uploadFolder(testUserPrefix, "", folderName, new MultipartFile[]{multipartFile1, multipartFile2});

        // Проверяем, что папка создана
        String fullFolderPath = testUserPrefix + folderName;
        assertTrue(folderExists(fullFolderPath), "Папка должна быть создана");

        // Проверяем, что файл file1.txt загружен
        String fullFilePath1 = fullFolderPath + file1Name;
        assertTrue(fileExists(fullFilePath1), "Файл file1.txt должен быть загружен");

        // Проверяем, что вложенная папка sub-folder создана
        String fullSubFolderPath = fullFolderPath + subFolderName;
        assertTrue(folderExists(fullSubFolderPath), "Вложенная папка sub-folder должна быть создана");

        // Проверяем, что файл file2.txt загружен
        String fullFilePath2 = fullFolderPath + file2Name;
        assertTrue(fileExists(fullFilePath2), "Файл file2.txt должен быть загружен");

        // Удаляем временные файлы
        Files.delete(tempFile1);
        Files.delete(tempFile2);
    }

    @Test
    @DisplayName("Загрузка папки во вложенную папку хранилища MinIO")
    void shouldUploadFolderToNestedFolder() throws IOException {
        // Создаем тестовые файлы
        String folderName = "my-folder/";
        String file1Name = "file1.txt";
        String subFolderName = "sub-folder/";
        String file2Name = "sub-folder/file2.txt";

        // Создаем временные файлы
        Path tempFile1 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile1, "Hello, file1!".getBytes());

        Path tempFile2 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile2, "Hello, file2!".getBytes());

        // Создаем MultipartFile из временных файлов
        MultipartFile multipartFile1 = new MockMultipartFile(
                "file",
                file1Name,
                "text/plain",
                Files.readAllBytes(tempFile1)
        );

        MultipartFile multipartFile2 = new MockMultipartFile(
                "file",
                file2Name,
                "text/plain",
                Files.readAllBytes(tempFile2)
        );

        // Создаем вложенную папку в MinIO
        String nestedFolderPath = "Projects/Java/CloudFileStorage/";
        minioService.createFolder(testUserPrefix, "", nestedFolderPath);

        // Загружаем папку во вложенную папку
        minioService.uploadFolder(testUserPrefix, nestedFolderPath, folderName, new MultipartFile[]{multipartFile1, multipartFile2});

        // Проверяем, что папка создана
        String fullFolderPath = testUserPrefix + nestedFolderPath + folderName;
        assertTrue(folderExists(fullFolderPath), "Папка должна быть создана");

        // Проверяем, что файл file1.txt загружен
        String fullFilePath1 = fullFolderPath + file1Name;
        assertTrue(fileExists(fullFilePath1), "Файл file1.txt должен быть загружен");

        // Проверяем, что вложенная папка sub-folder создана
        String fullSubFolderPath = fullFolderPath + subFolderName;
        assertTrue(folderExists(fullSubFolderPath), "Вложенная папка sub-folder должна быть создана");

        // Проверяем, что файл file2.txt загружен
        String fullFilePath2 = fullFolderPath + file2Name;
        assertTrue(fileExists(fullFilePath2), "Файл file2.txt должен быть загружен");

        // Удаляем временные файлы
        Files.delete(tempFile1);
        Files.delete(tempFile2);
    }

    @Test
    @DisplayName("Попытка загрузки папки в несуществующую папку")
    void shouldThrowExceptionWhenUploadingFolderToNonExistentFolder() throws IOException {
        // Создаем тестовые файлы
        String folderName = "my-folder/";
        String file1Name = "file1.txt";

        // Создаем временный файл
        Path tempFile1 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile1, "Hello, file1!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile1 = new MockMultipartFile(
                "file",
                file1Name,
                "text/plain",
                Files.readAllBytes(tempFile1)
        );

        // Пытаемся загрузить папку в несуществующую папку
        String nonExistentFolderPath = "NonExistentFolder/";
        assertThrows(FolderNotFoundException.class,
                () -> minioService.uploadFolder(testUserPrefix, nonExistentFolderPath, folderName, new MultipartFile[]{multipartFile1}),
                "Должно выбрасываться исключение при попытке загрузить папку в несуществующую папку");

        // Удаляем временный файл
        Files.delete(tempFile1);
    }

    @Test
    @DisplayName("Попытка загрузки папки с уже существующим именем")
    void shouldThrowExceptionWhenUploadingFolderWithExistingName() throws IOException {
        // Создаем тестовые файлы
        String folderName = "my-folder/";
        String file1Name = "file1.txt";

        // Создаем временный файл
        Path tempFile1 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile1, "Hello, file1!".getBytes());

        // Создаем MultipartFile из временного файла
        MultipartFile multipartFile1 = new MockMultipartFile(
                "file",
                file1Name,
                "text/plain",
                Files.readAllBytes(tempFile1)
        );

        // Создаем папку с таким же именем
        minioService.createFolder(testUserPrefix, "", folderName);

        // Пытаемся загрузить папку с уже существующим именем
        assertThrows(FolderAlreadyExistsException.class,
                () -> minioService.uploadFolder(testUserPrefix, "", folderName, new MultipartFile[]{multipartFile1}),
                "Должно выбрасываться исключение при попытке загрузить папку с уже существующим именем");

        // Удаляем временный файл
        Files.delete(tempFile1);
    }

    @Test
    @DisplayName("Загрузка папки с несколькими уровнями вложенности и множеством файлов")
    void shouldUploadFolderWithNestedFoldersAndMultipleFiles() throws IOException {
        // Создаем тестовые файлы
        String folderName = "my-folder/";
        String file1Name = "file1.txt";
        String subFolder1Name = "sub-folder1/";
        String file2Name = "sub-folder1/file2.txt";
        String file3Name = "sub-folder1/file3.txt"; // Добавляем второй файл в sub-folder1
        String subFolder2Name = "sub-folder1/sub-folder2/";
        String file4Name = "sub-folder1/sub-folder2/file4.txt";

        // Создаем временные файлы
        Path tempFile1 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile1, "Hello, file1!".getBytes());

        Path tempFile2 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile2, "Hello, file2!".getBytes());

        Path tempFile3 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile3, "Hello, file3!".getBytes());

        Path tempFile4 = Files.createTempFile("test-", ".txt");
        Files.write(tempFile4, "Hello, file4!".getBytes());

        // Создаем MultipartFile из временных файлов
        MultipartFile multipartFile1 = new MockMultipartFile(
                "file",
                file1Name,
                "text/plain",
                Files.readAllBytes(tempFile1)
        );

        MultipartFile multipartFile2 = new MockMultipartFile(
                "file",
                file2Name,
                "text/plain",
                Files.readAllBytes(tempFile2)
        );

        MultipartFile multipartFile3 = new MockMultipartFile(
                "file",
                file3Name,
                "text/plain",
                Files.readAllBytes(tempFile3)
        );

        MultipartFile multipartFile4 = new MockMultipartFile(
                "file",
                file4Name,
                "text/plain",
                Files.readAllBytes(tempFile4)
        );

        // Загружаем папку с несколькими уровнями вложенности
        minioService.uploadFolder(
                testUserPrefix,
                "",
                folderName,
                new MultipartFile[]{multipartFile1, multipartFile2, multipartFile3, multipartFile4}
        );

        // Проверяем, что папка создана
        String fullFolderPath = testUserPrefix + folderName;
        assertTrue(folderExists(fullFolderPath), "Папка должна быть создана");

        // Проверяем, что файл file1.txt загружен
        String fullFilePath1 = fullFolderPath + file1Name;
        assertTrue(fileExists(fullFilePath1), "Файл file1.txt должен быть загружен");

        // Проверяем, что вложенная папка sub-folder1 создана
        String fullSubFolder1Path = fullFolderPath + subFolder1Name;
        assertTrue(folderExists(fullSubFolder1Path), "Вложенная папка sub-folder1 должна быть создана");

        // Проверяем, что файл file2.txt загружен
        String fullFilePath2 = fullFolderPath + file2Name;
        assertTrue(fileExists(fullFilePath2), "Файл file2.txt должен быть загружен");

        // Проверяем, что файл file3.txt загружен
        String fullFilePath3 = fullFolderPath + file3Name;
        assertTrue(fileExists(fullFilePath3), "Файл file3.txt должен быть загружен");

        // Проверяем, что вложенная папка sub-folder2 создана
        String fullSubFolder2Path = fullFolderPath + subFolder2Name;
        assertTrue(folderExists(fullSubFolder2Path), "Вложенная папка sub-folder2 должна быть создана");

        // Проверяем, что файл file4.txt загружен
        String fullFilePath4 = fullFolderPath + file4Name;
        assertTrue(fileExists(fullFilePath4), "Файл file4.txt должен быть загружен");

        // Удаляем временные файлы
        Files.delete(tempFile1);
        Files.delete(tempFile2);
        Files.delete(tempFile3);
        Files.delete(tempFile4);
    }

    @Test
    @DisplayName("Успешное скачивание файла из корневой папки пользователя")
    void shouldDownloadFileFromRootFolderSuccessfully() throws Exception {
        // Arrange
        String folderPath = ""; // Пустая строка, так как файл находится в корневой папке
        String fileName = "report.pdf";
        String fullPath = testUserPrefix + fileName; // Полный путь к файлу

        // Загружаем тестовый файл в MinIO (в корневую папку пользователя)
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullPath)
                        .stream(new ByteArrayInputStream("file content".getBytes()), 12, -1)
                        .build()
        );

        // Act
        InputStreamResource result = minioService.downloadFile(testUserPrefix, folderPath, fileName);

        // Assert
        assertNotNull(result);
        assertEquals("file content", new String(result.getInputStream().readAllBytes()));
    }

    @Test
    @DisplayName("Успешное скачивание файла из вложенной папки")
    void shouldDownloadFileFromNestedFolderSuccessfully() throws Exception {
        // Arrange
        String folderPath = "documents/";
        String fileName = "report.pdf";
        String fullPath = testUserPrefix + folderPath + fileName;

        // Загружаем тестовый файл в MinIO
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullPath)
                        .stream(new ByteArrayInputStream("file content".getBytes()), 12, -1)
                        .build()
        );

        // Act
        InputStreamResource result = minioService.downloadFile(testUserPrefix, folderPath, fileName);

        // Assert
        assertNotNull(result);
        assertEquals("file content", new String(result.getInputStream().readAllBytes()));
    }

    @Test
    @DisplayName("Должен выкидывать исключение при скачивании несуществующего файла")
    void shouldThrowExceptionWhenDownloadingNonExistentFile() {
        // Arrange
        String folderPath = "documents/";
        String fileName = "report.pdf";

        // Act & Assert
        assertThrows(
                FileNotFoundInStorageException.class,
                () -> minioService.downloadFile(testUserPrefix, folderPath, fileName)
        );
    }

    @Test
    @DisplayName("Успешное скачивание папки в виде ZIP-архива")
    void shouldDownloadFolderAsZipSuccessfully() throws Exception {
        // Arrange
        String folderPath = "documents/";
        String folderName = "my-folder";
        String fullFolderPath = testUserPrefix + folderPath + folderName + "/";

        // Загружаем тестовые файлы в MinIO
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullFolderPath + "file1.txt")
                        .stream(new ByteArrayInputStream("file1 content".getBytes()), 13, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullFolderPath + "sub-folder/file2.txt")
                        .stream(new ByteArrayInputStream("file2 content".getBytes()), 13, -1)
                        .build()
        );

        // Добавляем пустые объекты для папок
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + folderPath)
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullFolderPath)
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullFolderPath + "sub-folder/another-empty-folder/")
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );

        // Act
        InputStreamResource result = minioService.downloadFolder(testUserPrefix, folderPath, folderName);

        // Assert
        assertNotNull(result);

        // Проверяем содержимое ZIP-архива
        try (ZipInputStream zipIn = new ZipInputStream(result.getInputStream())) {
            ZipEntry entry;
            Map<String, String> files = new HashMap<>();
            while ((entry = zipIn.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zipIn.readAllBytes()));
            }

            assertEquals(2, files.size());
            assertEquals("file1 content", files.get("file1.txt"));
            assertEquals("file2 content", files.get("sub-folder/file2.txt"));
        }
    }

    @Test
    @DisplayName("Попытка скачивания несуществующей папки")
    void shouldThrowExceptionWhenFolderNotFound() {
        // Arrange
        String folderPath = "documents/";
        String folderName = "non-existent-folder";

        // Act & Assert
        FolderNotFoundException exception = assertThrows(
                FolderNotFoundException.class,
                () -> minioService.downloadFolder(testUserPrefix, folderPath, folderName)
        );
        assertEquals("Folder does not exist: " + testUserPrefix + folderPath + folderName + "/", exception.getMessage());
    }

    @Test
    @DisplayName("Скачивание папки с файлами, содержащими специальные символы в именах")
    void shouldDownloadFolderWithSpecialCharactersInFileNames() throws Exception {
        // Arrange
        String folderPath = "documents/";
        String folderName = "special-chars-folder";
        String fullFolderPath = testUserPrefix + folderPath + folderName + "/";

        // Загружаем тестовые файлы в MinIO
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullFolderPath + "file with spaces.txt")
                        .stream(new ByteArrayInputStream("file with spaces content".getBytes()), 24, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullFolderPath + "файл-на-кириллице.txt")
                        .stream(new ByteArrayInputStream("файл-на-кириллице content".getBytes()),
                                "файл-на-кириллице content".getBytes().length, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullFolderPath + "file#with#special#chars.txt")
                        .stream(new ByteArrayInputStream("file#with#special#chars content".getBytes()),
                                "file#with#special#chars content".getBytes().length, -1)
                        .build()
        );
        // Добавляем пустые объекты для папки
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(fullFolderPath)
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );

        // Act
        InputStreamResource result = minioService.downloadFolder(testUserPrefix, folderPath, folderName);

        // Assert
        assertNotNull(result);

        // Проверяем содержимое ZIP-архива
        try (ZipInputStream zipIn = new ZipInputStream(result.getInputStream())) {
            ZipEntry entry;
            Map<String, String> files = new HashMap<>();
            while ((entry = zipIn.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zipIn.readAllBytes()));
            }

            assertEquals(3, files.size());
            assertEquals("file with spaces content", files.get("file with spaces.txt"));
            assertEquals("файл-на-кириллице content", files.get("файл-на-кириллице.txt"));
            assertEquals("file#with#special#chars content", files.get("file#with#special#chars.txt"));
        }
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

    private boolean fileExists(String filePath){
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
            throw new RuntimeException("Failed to check if folder exists: " + filePath, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to check if folder exists: " + filePath, e);
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

//    @Test
//    @DisplayName("Оценка быстродействия переименования папки с 900 файлами") // TODO попробовать добавить многопоточку/асинхрон и проверить будет ли быстрее
//    void shouldRenameFolderWith900Files() throws InterruptedException {
//        // Имена папок
//        String parentFolder = "parentFolder/";
//        String contentFolder = "contentFolder/";
//        String nestedFolder = "nestedFolder/";
//        String renamedContentFolder = "renamedContentFolder/";
//
//        // Создаем родительскую папку
//        minioService.createFolder(testUserPrefix, "", parentFolder);
//
//        // Создаем папку с контентом
//        minioService.createFolder(testUserPrefix, parentFolder, contentFolder);
//
//        long startAddingTime = System.currentTimeMillis();
//
//        // Добавляем 450 файлов в папку с контентом
//        addFilesToFolder(testUserPrefix + parentFolder + contentFolder, 450, 100 * 1024 * 1024 / 900); // ~100 Мб / 900 файлов
//
//        // Создаем вложенную папку и добавляем 450 файлов
//        minioService.createFolder(testUserPrefix, parentFolder + contentFolder, nestedFolder);
//        addFilesToFolder(testUserPrefix + parentFolder + contentFolder + nestedFolder, 450, 100 * 1024 * 1024 / 900);
//
//        long endAddingTime = System.currentTimeMillis();
//        System.out.println("Время добавления файлов: " + (endAddingTime - startAddingTime) + " мс");
//
//        // Замеряем время выполнения переименования
//        long startTime = System.currentTimeMillis();
//        minioService.renameFolder(testUserPrefix, parentFolder, contentFolder, renamedContentFolder);
//        long endTime = System.currentTimeMillis();
//
//        // Выводим время выполнения
//        long duration = endTime - startTime;
//        System.out.println("Время выполнения переименования: " + duration + " мс");
//
//        // Проверяем, что старые объекты удалены
//        assertFalse(folderExists(testUserPrefix + parentFolder + contentFolder));
//        assertFalse(folderExists(testUserPrefix + parentFolder + contentFolder + nestedFolder));
//
//        // Проверяем, что новые объекты созданы
//        assertTrue(folderExists(testUserPrefix + parentFolder + renamedContentFolder));
//        assertTrue(folderExists(testUserPrefix + parentFolder + renamedContentFolder + nestedFolder));
//
//        // Проверяем, что все файлы переименовались
//        assertTrue(checkFilesRenamed(testUserPrefix + parentFolder + contentFolder, testUserPrefix + parentFolder + renamedContentFolder, 902));
//    }
//
//    private void addFilesToFolder(String folderPath, int fileCount, int fileSize) {
//        byte[] content = new byte[fileSize];
//        new Random().nextBytes(content); // Заполняем массив случайными данными
//
//        for (int i = 0; i < fileCount; i++) {
//            String fileName = "file" + i + ".txt";
//            try {
//                minioClient.putObject(
//                        PutObjectArgs.builder()
//                                .bucket(TEST_BUCKET_NAME)
//                                .object(folderPath + fileName)
//                                .stream(new ByteArrayInputStream(content), content.length, -1)
//                                .build()
//                );
//            } catch (Exception e) {
//                throw new RuntimeException("Failed to add file: " + folderPath + fileName, e);
//            }
//        }
//    }
//
//    private boolean checkFilesRenamed(String oldFolderPath, String newFolderPath, int expectedFileCount) {
//
//        try {
//            // Получаем список всех объектов в старой папке
//            Iterable<Result<Item>> oldResults = minioClient.listObjects(
//                    ListObjectsArgs.builder()
//                            .bucket(TEST_BUCKET_NAME)
//                            .prefix(oldFolderPath)
//                            .recursive(true)
//                            .build()
//            );
//
//            // Получаем список всех объектов в новой папке
//            Iterable<Result<Item>> newResults = minioClient.listObjects(
//                    ListObjectsArgs.builder()
//                            .bucket(TEST_BUCKET_NAME)
//                            .prefix(newFolderPath)
//                            .recursive(true)
//                            .build()
//            );
//
//            // Считаем количество объектов в старой и новой папке
//            int oldCount = 0;
//            for (Result<Item> result : oldResults) {
//                oldCount++;
//            }
//
//            int newCount = 0;
//            for (Result<Item> result : newResults) {
//                newCount++;
//            }
//
//            // Если в старой папке объектов нет, а в новой есть ожидаемое количество, считаем, что всё переименовано
//            return oldCount == 0 && newCount == expectedFileCount;
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to check files renaming", e);
//        }
//    }
}
