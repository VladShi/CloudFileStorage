package ru.vladshi.cloudfilestorage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vladshi.cloudfilestorage.BaseTestcontainersForTest;
import ru.vladshi.cloudfilestorage.dto.StorageItem;
import ru.vladshi.cloudfilestorage.exception.FileAlreadyExistsInStorageException;
import ru.vladshi.cloudfilestorage.exception.FileNotFoundInStorageException;
import ru.vladshi.cloudfilestorage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.exception.FolderNotFoundException;

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

    private static String testUserPrefix;

    private static final String ROOT_FOLDER_PATH = "";
    private static final String FOLDER_0_LVL_NAME = "folder_0_Lvl";
    private static final String FOLDER_0_LVL_PATH = "/" + FOLDER_0_LVL_NAME + "/"; // /folder1Lvl/
    private static final String FOLDER_1_LVL_NAME = "folder_1_Lvl";
    private static final String FOLDER_1_LVL_PATH = FOLDER_0_LVL_PATH + FOLDER_1_LVL_NAME + "/"; // /folder0Lvl/folder1Lvl/
    private static final String FOLDER_2_LVL_NAME = "folder_2_Lvl";
    private static final String FOLDER_2_LVL_PATH = FOLDER_1_LVL_PATH + FOLDER_2_LVL_NAME + "/"; // /folder0Lvl/folder1Lvl/folder2Lvl/
    private static final String NON_EXISTENT_FOLDER_NAME = "nonExistentFolder1";
    private static final String NON_EXISTENT_FOLDER_PATH = "/" + NON_EXISTENT_FOLDER_NAME + "/";

    private static Path tempFilePath;
    private static MultipartFile multipartFile;

    @BeforeAll
    public static void init() throws IOException {
        testUserPrefix = "testuser";

        // Создаем временный файл и MultipartFile
        tempFilePath = Files.createTempFile("test-", ".txt");
        Files.write(tempFilePath, "Hello, MinIO!".getBytes());

        multipartFile = new MockMultipartFile(
                "file",
                "test-file.txt",
                "text/plain",
                Files.readAllBytes(tempFilePath)
        );
    }

    @AfterAll
    public static void cleanup() throws IOException {
        if (tempFilePath != null) {
            Files.delete(tempFilePath);
        }
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

        List<StorageItem> items = minioService.getItems(testUserPrefix, ROOT_FOLDER_PATH);

        assertTrue(items.isEmpty(), "Список должен быть пустым, если у пользователя нет папок и файлов");
    }

    @Test
    @DisplayName("Создание папки в корневой папке пользователя")
    void shouldCreateFolderInRootUserPath() {
        // Сначала создаем папку
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH),
                "Папка должна быть создана в корневой папке пользователя");
    }

    @Test
    @DisplayName("Проверяем, что метод getItems возвращает пустой список, если в папке нет папок и файлов")
    void shouldReturnEmptyListIfFolderHasNoFoldersAndFiles() {
        // Сначала создаем папку
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Папка должна быть создана в basePath");

        List<StorageItem> items = minioService.getItems(testUserPrefix, FOLDER_0_LVL_NAME);

        assertTrue(items.isEmpty(), "Список должен быть пустым, если в папке нет папок и файлов");
    }

    @Test
    @DisplayName("Создание вложенной папки на два уровня")
    void shouldCreateNestedFolder() {
        // Сначала создаем промежуточную папку
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        // Затем создаем вложенную папку
        minioService.createFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);

        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH),
                "Промежуточная папка должна быть создана");
        assertTrue(folderExists(testUserPrefix + FOLDER_1_LVL_PATH),
                "Вложенная папка должна быть создана");
    }

    @Test
    @DisplayName("Проверяем, что метод getItems возвращает вложенную папку, если в папке есть одна вложенная папка")
    void shouldReturnListWithInnerFolderIfFolderHasInnerFolder() {
        // Создаем папку верхнего уровня
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        // Создаем вложенную папку
        minioService.createFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);

        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Папка должна быть создана в basePath");
        assertTrue(folderExists(testUserPrefix + FOLDER_1_LVL_PATH),
                "В запрашиваемой папке должна быть создана внутренняя папка");

        List<StorageItem> items = minioService.getItems(testUserPrefix, FOLDER_0_LVL_PATH);

        assertFalse(items.isEmpty(), "Список не должен быть пустым");
        assertEquals(1, items.size(), "Размер списка элементов должен быть равен 1");
        assertEquals(FOLDER_1_LVL_PATH, items.getFirst().relativePath(), "Должны получить внутреннюю папку");
    }

    @Test
    @DisplayName("Попытка создания вложенной папки в несуществующей папке")
    void shouldThrowExceptionWhenPathForNewPathDoesNotExist() {
        assertFalse(folderExists(testUserPrefix + NON_EXISTENT_FOLDER_PATH),
                "Промежуточная папка не должна существовать");

        assertThrows(FolderNotFoundException.class,
                () -> minioService.createFolder(testUserPrefix, NON_EXISTENT_FOLDER_PATH, FOLDER_1_LVL_NAME),
                "Должно выбрасываться исключение при попытке создать папку в несуществующей папке");
    }

    @Test
    @DisplayName("Попытка создания папки, которая уже существует")
    void shouldThrowExceptionWhenFolderAlreadyExists() {
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Папка должна быть создана");

        assertThrows(FolderAlreadyExistsException.class,
                () -> minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME),
                "Должно выбрасываться исключение при попытке создать существующую папку");
    }

    @Test
    @DisplayName("Попытка создания папки со значением null для имени")
    void shouldThrowExceptionWhenFolderNameIsNull() {

        String nullFolderName = null;

        assertThrows(IllegalArgumentException.class,
                () -> minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, nullFolderName),
                "Должно выбрасываться исключение при попытке создать папку со значением null для имени");
    }

    @Test
    @DisplayName("Попытка создания папки с пустым именем")
    void shouldThrowExceptionWhenFolderNameIsEmpty() {

        String folderWithEmptyName = "";

        assertThrows(IllegalArgumentException.class,
                () -> minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, folderWithEmptyName),
                "Должно выбрасываться исключение при попытке создать папку с пустым именем");
    }

    @Test
    @DisplayName("Удаление пустой папки")
    void shouldDeleteEmptyFolder() {
        // Создаем папку
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Папка для удаления должна существовать");

        minioService.deleteFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertFalse(folderExists(testUserPrefix + FOLDER_0_LVL_PATH),
                "Папка не должна существовать после удаления");
    }

    @Test
    @DisplayName("Удаление папки с вложенными объектами")
    void shouldDeleteFolderWithNestedObjects() {
        // Создаем папку и вложенные объекты
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_0_LVL_PATH , FOLDER_1_LVL_NAME);
        // TODO Здесь можно добавить файлы, если нужно

        // Убедимся, что папка и вложенные объекты существуют
        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Папка для удаления должна существовать");
        assertTrue(folderExists(testUserPrefix + FOLDER_1_LVL_PATH),
                "Папка вложенная в папку для удаления должна существовать");

        // Удаляем папку содержащую вложенные объекты
        minioService.deleteFolder(testUserPrefix, "", FOLDER_0_LVL_NAME);

        // Проверяем, что папка и вложенные объекты удалены
        assertFalse(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Папка не должна существовать, после удаления");
        assertFalse(folderExists(testUserPrefix + FOLDER_1_LVL_PATH),
                "Папка вложенная в удаленную папку не должна существовать, после удаления родительской папки");
    }

    @Test
    @DisplayName("Удаление папки с двумя уровнями вложенности")
    void shouldDeleteFolderWithTwoLevelsOfNesting() {
        // Создаем папку и вложенные объекты
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_1_LVL_PATH, FOLDER_2_LVL_NAME);

        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Папка для удаления должна существовать");
        assertTrue(folderExists(testUserPrefix + FOLDER_1_LVL_PATH ),
                "Папка вложенная в папку для удаления должна существовать");
        assertTrue(folderExists(testUserPrefix + FOLDER_2_LVL_PATH),
                "Папка вложенная на два уровня в папку для удаления должна существовать");

        // Удаляем папку верхнего уровня
        minioService.deleteFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertFalse(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Папка не должна существовать, после удаления");
        assertFalse(folderExists(testUserPrefix + FOLDER_1_LVL_PATH),
                "Папка вложенная в удаленную папку не должна существовать, после удаления родительской папки");
        assertFalse(folderExists(testUserPrefix + FOLDER_2_LVL_PATH),
                "Папка вложенная на два уровня в удаленную папку не должна существовать, после удаления папки верхнего уровня");
    }

    @Test
    @DisplayName("Удаление вложенной папки и проверка существования родительской папки")
    void shouldDeleteNestedFolderButKeepParentFolder() {
        // Создаем папку и вложенные объекты
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_1_LVL_PATH, FOLDER_2_LVL_NAME);

        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Папка должна существовать");
        assertTrue(folderExists(testUserPrefix + FOLDER_1_LVL_PATH), "Папка должна существовать");
        assertTrue(folderExists(testUserPrefix + FOLDER_2_LVL_PATH), "Папка должна существовать");

        // Удаляем вложенную папку первого уровня
        minioService.deleteFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);

        assertFalse(folderExists(testUserPrefix + FOLDER_1_LVL_PATH), "Папка не должна существовать после удаления");
        assertFalse(folderExists(testUserPrefix + FOLDER_2_LVL_PATH), "Папка не должна существовать после удаления родительской папки");

        assertTrue(folderExists(testUserPrefix + FOLDER_0_LVL_PATH), "Родительская папка должна существовать после удаления вложенных");
    }

    @Test
    @DisplayName("Попытка удаления несуществующей папки")
    void shouldThrowExceptionWhenDeletingNonExistentFolder() {
        assertFalse(folderExists(testUserPrefix + NON_EXISTENT_FOLDER_PATH),
                "Папка для удаления не должна существовать");

        assertThrows(FolderNotFoundException.class,
                () -> minioService.deleteFolder(testUserPrefix, ROOT_FOLDER_PATH, NON_EXISTENT_FOLDER_NAME),
                "Должно выбрасываться исключение при попытке удаления несуществующей папки");
    }

    @Test
    @DisplayName("Переименование пустой папки в корневой папке пользователя")
    void shouldRenameFolderWithEmptyPath() {
        String renamedFolderName = "renamedFolder";
        String renamedFolderPath = "/" + renamedFolderName + "/";

        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        minioService.renameFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME, renamedFolderName);

        assertFalse(folderExists(testUserPrefix + FOLDER_0_LVL_PATH),
                "Папка не должна существовать после её переименования");

        assertTrue(folderExists(testUserPrefix + renamedFolderPath),
                "Папка должна существовать с новым именем, после переименования");
    }

    @Test
    @DisplayName("Переименование пустой вложенной папки")
    void shouldRenameEmptyFolder() {
        String renamedFolderName = "renamedEmptyFolder";
        String renamedFolderPath = FOLDER_0_LVL_PATH + renamedFolderName + "/";

        // Создаем родительскую папку и пустую вложенную папку
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);

        minioService.renameFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME, renamedFolderName);

        assertFalse(folderExists(testUserPrefix + FOLDER_1_LVL_PATH),
                "Папка не должна существовать после её переименования");

        assertTrue(folderExists(testUserPrefix + renamedFolderPath),
                "Папка должна существовать с новым именем, после переименования");
    }

    @Test
    @DisplayName("Попытка переименования несуществующей папки")
    void shouldThrowExceptionWhenRenamingNonExistentFolder() {

        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertThrows(FolderNotFoundException.class,
                () -> minioService.renameFolder(
                        testUserPrefix, FOLDER_0_LVL_PATH, NON_EXISTENT_FOLDER_NAME, "renamedFolderName"),
                "Должно выбрасываться исключение при попытке переименования несуществующей папки");
    }

    @Test
    @DisplayName("Попытка переименования папки в уже существующую папку")
    void shouldThrowExceptionWhenRenamingToExistingFolder() {
        String existingFolderName = "existingFolder";

        // Создаем родительскую папку и две вложенные папки
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_0_LVL_PATH, existingFolderName);

        assertThrows(FolderAlreadyExistsException.class,
                () -> minioService.renameFolder(
                        testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME, existingFolderName),
                "Должно выбрасываться исключение" +
                        " при попытке переименования с названием уже существующей папки");
    }

    @Test
    @DisplayName("Переименование папки с вложенными объектами")
    void shouldRenameFolderWithNestedFolders() {
        String renamedFolderName = "renamed-folder";
        String renamedFolderPath = FOLDER_0_LVL_PATH + renamedFolderName + "/";

        // Создаем папку и вложенные объекты
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);
        minioService.createFolder(testUserPrefix, FOLDER_1_LVL_PATH, FOLDER_2_LVL_NAME);
        // minioService.uploadFile(); // TODO добавить файлы

        // Переименовываем папку с одним уровнем вложенности
        minioService.renameFolder(testUserPrefix, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME, renamedFolderName);

        // Проверяем, что старые объекты удалены
        assertFalse(folderExists(testUserPrefix + FOLDER_1_LVL_PATH),
                "Папка не должна существовать после её переименовании");
        assertFalse(folderExists(testUserPrefix + FOLDER_2_LVL_PATH),
                "После переименования родительской папки " +
                        "вложенная папка не должна существовать по первоначальному пути");
        // assertFalse(fileExists());

        assertTrue(folderExists(testUserPrefix + renamedFolderPath),
                "Папка должна существовать с новым именем, после переименования");
        assertTrue(folderExists(testUserPrefix + renamedFolderPath + FOLDER_2_LVL_NAME + "/"),
                "После переименования родительской папки вложенная папка должна существовать по новому пути");
        // assertTrue(fileExists());
    }

    @Test
    @DisplayName("Загрузка файла в корневую папку пользователя")
    void shouldUploadFileToBaseFolder() {
        minioService.uploadFile(testUserPrefix, ROOT_FOLDER_PATH, multipartFile);

        String fullFilePath = testUserPrefix + "/" + multipartFile.getOriginalFilename();
        assertTrue(fileExists(fullFilePath), "Файл должен быть загружен в корневую папку пользователя");
    }

    @Test
    @DisplayName("Загрузка файла во вложенную папку")
    void shouldUploadFileToNestedFolder() {
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        minioService.uploadFile(testUserPrefix, FOLDER_0_LVL_PATH, multipartFile);

        String fullFilePath = testUserPrefix + FOLDER_0_LVL_PATH + multipartFile.getOriginalFilename();
        assertTrue(fileExists(fullFilePath), "Файл должен быть загружен во вложенную папку");
    }

    @Test
    @DisplayName("Попытка загрузки файла, который уже существует")
    void shouldThrowExceptionWhenUploadingExistingFile() {
        // Загружаем файл в первый раз
        minioService.uploadFile(testUserPrefix, ROOT_FOLDER_PATH, multipartFile);

        // Проверяем, что файл загружен
        String fullFilePath = testUserPrefix + "/" + multipartFile.getOriginalFilename();
        assertTrue(fileExists(fullFilePath), "Файл должен быть загружен в MinIO");

        // Пытаемся загрузить файл с тем же именем
        assertThrows(FileAlreadyExistsInStorageException.class,
                () -> minioService.uploadFile(testUserPrefix, ROOT_FOLDER_PATH, multipartFile),
                "Должно выбрасываться исключение при попытке загрузить файл с именем как у существующего файла");
    }

    @Test
    @DisplayName("Попытка загрузки файла с пустым именем")
    void shouldThrowExceptionWhenUploadingFileWithEmptyName() throws IOException {
        // Создаем MultipartFile с пустым именем
        MultipartFile emptyNameFile = new MockMultipartFile(
                "file",
                "",
                "text/plain",
                multipartFile.getBytes()
        );

        // Пытаемся загрузить файл с пустым именем
        assertThrows(IllegalArgumentException.class,
                () -> minioService.uploadFile(testUserPrefix, ROOT_FOLDER_PATH, emptyNameFile),
                "Должно выбрасываться исключение при попытке загрузить файл с пустым именем");
    }

    @Test
    @DisplayName("Попытка загрузки файла с некорректным именем")
    void shouldThrowExceptionWhenUploadingFileWithInvalidName() throws IOException {
        // Создаем MultipartFile с некорректным именем
        MultipartFile invalidNameFile = new MockMultipartFile(
                "file",
                "../invalid-file.txt",
                "text/plain",
                multipartFile.getBytes()
        );

        // Пытаемся загрузить файл с некорректным именем
        Exception exception = assertThrows(RuntimeException.class,
                () -> minioService.uploadFile(testUserPrefix, ROOT_FOLDER_PATH, invalidNameFile),
                "Должно выбрасываться исключение при попытке загрузить файл с некорректным именем");

        // Проверяем, что причина исключения - IllegalArgumentException
        assertInstanceOf(IllegalArgumentException.class, exception.getCause(),
                "Причина исключения должна быть IllegalArgumentException");
    }

    @Test
    @DisplayName("Удаление существующего файла")
    void shouldDeleteExistingFile() {
        minioService.uploadFile(testUserPrefix, ROOT_FOLDER_PATH, multipartFile);

        String fullFilePath = testUserPrefix + "/" + multipartFile.getOriginalFilename();
        assertTrue(fileExists(fullFilePath), "Файл должен существовать в корневой папке пользователя");

        minioService.deleteFile(testUserPrefix, ROOT_FOLDER_PATH, multipartFile.getOriginalFilename());

        assertFalse(fileExists(fullFilePath), "Файл не должен существовать после удаления");
    }

    @Test
    @DisplayName("Попытка удаления несуществующего файла")
    void shouldThrowExceptionWhenDeletingNonExistentFile() {
        String nonExistentFileName = "non-existent-file.txt";

        assertFalse(fileExists(testUserPrefix + "/" + nonExistentFileName), "Файл не должен существовать в MinIO");

        // Пытаемся удалить несуществующий файл
        assertThrows(FileNotFoundInStorageException.class,
                () -> minioService.deleteFile(testUserPrefix, ROOT_FOLDER_PATH, nonExistentFileName),
                "Должно выбрасываться исключение при попытке удалить несуществующий файл");
    }

    @Test
    @DisplayName("Попытка удаления файла с пустым именем")
    void shouldThrowExceptionWhenDeletingFileWithEmptyName() {
        String emptyFileName = "";

        assertThrows(IllegalArgumentException.class,
                () -> minioService.deleteFile(testUserPrefix, ROOT_FOLDER_PATH, emptyFileName),
                "Должно выбрасываться исключение при попытке удалить файл с пустым именем");
    }

    @Test
    @DisplayName("Удаление файла из вложенной папки")
    void shouldDeleteFileFromNestedFolder() {
        minioService.createFolder(testUserPrefix, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        minioService.uploadFile(testUserPrefix, FOLDER_0_LVL_PATH, multipartFile);

        String fullFilePath = testUserPrefix + FOLDER_0_LVL_PATH + multipartFile.getOriginalFilename();
        assertTrue(fileExists(fullFilePath), "Файл должен существовать во вложенной папке");

        minioService.deleteFile(testUserPrefix, FOLDER_0_LVL_PATH, multipartFile.getOriginalFilename());

        assertFalse(fileExists(fullFilePath), "Файл не должен существовать после удаления");
    }

    @Test
    @DisplayName("Попытка удаления файла с некорректным именем")
    void shouldThrowExceptionWhenDeletingFileWithInvalidName() {
        String invalidFileName = "../invalid-file.txt";

        // Пытаемся удалить файл с некорректным именем
        Exception exception = assertThrows(RuntimeException.class,
                () -> minioService.deleteFile(testUserPrefix, ROOT_FOLDER_PATH, invalidFileName),
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

    @Test
    @DisplayName("Поиск файлов и папок по имени")
    void shouldSearchFilesByName() throws Exception {
        // Arrange
        String query = "мокр";

        // Загружаем тестовые файлы в MinIO
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + "мокрые/")
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + "мокрые/мокрый зонт.jpg")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + "мокрые/дырочки.jpg")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );

        // Act
        List<StorageItem> results = minioService.searchItems(testUserPrefix, query);

        // Assert
        assertEquals(2, results.size());
        assertEquals(testUserPrefix + "мокрые/", results.get(0).relativePath());
        assertEquals(testUserPrefix + "мокрые/мокрый зонт.jpg", results.get(1).relativePath());
    }

    @Test
    @DisplayName("Поиск файлов в корневой папке пользователя")
    void shouldSearchFilesInRootFolder() throws Exception {
        // Arrange
        String query = "file";

        // Загружаем тестовые файлы в MinIO
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + "file1.txt")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + "file2.txt")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + "image.jpg")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );

        // Act
        List<StorageItem> results = minioService.searchItems(testUserPrefix, query);

        // Assert
        assertEquals(2, results.size());
        assertEquals(testUserPrefix + "file1.txt", results.get(0).relativePath());
        assertEquals(testUserPrefix + "file2.txt", results.get(1).relativePath());
    }

    @Test
    @DisplayName("Поиск файлов с учётом регистра")
    void shouldSearchFilesCaseInsensitive() throws Exception {
        // Arrange
        String query = "FiLe";

        // Загружаем тестовые файлы в MinIO
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + "file1.txt")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + "File2.txt")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(testUserPrefix + "image.jpg")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );

        // Act
        List<StorageItem> results = minioService.searchItems(testUserPrefix, query);

        // Assert
        assertEquals(2, results.size());
        assertEquals(testUserPrefix + "File2.txt", results.get(0).relativePath());
        assertEquals(testUserPrefix + "file1.txt", results.get(1).relativePath());
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

    //TODO проверка что создание/удаление папки у одного пользователя не влияет на другого

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
