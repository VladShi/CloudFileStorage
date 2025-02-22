package ru.vladshi.cloudfilestorage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.vladshi.cloudfilestorage.dto.StorageItem;
import ru.vladshi.cloudfilestorage.exception.FileAlreadyExistsInStorageException;
import ru.vladshi.cloudfilestorage.exception.FileNotFoundInStorageException;
import ru.vladshi.cloudfilestorage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.exception.FolderNotFoundException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {
        MinioServiceImpl.class,
        MinioServiceImplTest.MinioClientConfig.class
        }, properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
@Testcontainers
public class MinioServiceImplTest {

    @Autowired
    private MinioService minioService;

    @Autowired
    private MinioClient minioClient;

    protected final static String TEST_BUCKET_NAME = "test-bucket";
    private static final String TEST_USER_PREFIX = "testuser";

    private static final String ROOT_FOLDER_PATH = "";
    private static final String FOLDER_0_LVL_NAME = "folder_0_Lvl";
    private static final String FOLDER_0_LVL_PATH = "/" + FOLDER_0_LVL_NAME + "/"; // /folder1Lvl/
    private static final String FOLDER_1_LVL_NAME = "folder_1_Lvl";
    private static final String FOLDER_1_LVL_PATH = FOLDER_0_LVL_PATH + FOLDER_1_LVL_NAME + "/"; // /folder0Lvl/folder1Lvl/
    private static final String FOLDER_2_LVL_NAME = "folder_2_Lvl";
    private static final String FOLDER_2_LVL_PATH = FOLDER_1_LVL_PATH + FOLDER_2_LVL_NAME + "/"; // /folder0Lvl/folder1Lvl/folder2Lvl/
    private static final String NON_EXISTENT_FOLDER_NAME = "nonExistentFolder1";
    private static final String NON_EXISTENT_FOLDER_PATH = "/" + NON_EXISTENT_FOLDER_NAME + "/";

    private static final String TEST_FILE_NAME = "test-file.txt";
    private static final String TEST_FILE_CONTENT = "Hello, MinIO!";
    private static final String RENAMED_FILE_NAME = "renamed-file.txt";
    private static final MultipartFile MULTIPART_TEST_FILE = new MockMultipartFile(
            TEST_FILE_NAME,
            TEST_FILE_NAME,
            "text/plain",
            TEST_FILE_CONTENT.getBytes()
    );

    private static final String MINIO_IMAGE = "minio/minio:RELEASE.2024-12-18T13-15-44Z";

    @Container
    protected static final MinIOContainer minioContainer = new MinIOContainer(DockerImageName.parse(MINIO_IMAGE))
//            .withReuse(true) // убрать после создания тестов, вернуть управление @Container
            .withUserName("minioadmin")
            .withPassword("minioadmin")
            .withExposedPorts(9000)
//            .withCreateContainerCmdModifier(
//                    cmd -> cmd.withName("minio-test"))
    ;

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

//        minioContainer.start(); // убрать после создания тестов, вернуть управление @Container

        String endpoint = "http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort(9000);
        registry.add("minio.endpoint", () -> endpoint);
        registry.add("minio.accessKey", minioContainer::getUserName);
        registry.add("minio.secretKey", minioContainer::getPassword);
        registry.add("minio.bucket.users", () -> TEST_BUCKET_NAME);
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
                        .prefix(TEST_USER_PREFIX)
                        .recursive(false)
                        .build()
        );

        assertFalse(results.iterator().hasNext(), "В MinIO не должно быть объектов с префиксом пользователя");

        List<StorageItem> items = minioService.getItems(TEST_USER_PREFIX, ROOT_FOLDER_PATH);

        assertTrue(items.isEmpty(), "Список должен быть пустым, если у пользователя нет папок и файлов");
    }

    @Test
    @DisplayName("Создание папки в корневой папке пользователя")
    void shouldCreateFolderInRootUserPath() {
        // Сначала создаем папку
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH),
                "Папка должна быть создана в корневой папке пользователя");
    }

    @Test
    @DisplayName("Проверяем, что метод getItems возвращает пустой список, если в папке нет папок и файлов")
    void shouldReturnEmptyListIfFolderHasNoFoldersAndFiles() {
        // Сначала создаем папку
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Папка должна быть создана в basePath");

        List<StorageItem> items = minioService.getItems(TEST_USER_PREFIX, FOLDER_0_LVL_PATH);

        assertTrue(items.isEmpty(), "Список должен быть пустым, если в папке нет папок и файлов");
    }

    @Test
    @DisplayName("Запрашиваем методом getItems содержимое несуществующей папки")
    void shouldThrowExceptionWhenListedFolderDoesNotExist() {

        assertFalse(folderExists(TEST_USER_PREFIX + NON_EXISTENT_FOLDER_PATH), "Папка не должна существовать");

        assertThrows(FolderNotFoundException.class,
                () -> minioService.getItems(TEST_USER_PREFIX, NON_EXISTENT_FOLDER_PATH),
                "Должно выбрасываться исключение при запросе содержимого несуществующей папки");
    }

    @Test
    @DisplayName("Создание вложенной папки на два уровня")
    void shouldCreateNestedFolder() {
        // Сначала создаем промежуточную папку
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        // Затем создаем вложенную папку
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);

        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH),
                "Промежуточная папка должна быть создана");
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH),
                "Вложенная папка должна быть создана");
    }

    @Test
    @DisplayName("Проверяем, что метод getItems возвращает вложенную папку, если в папке есть одна вложенная папка")
    void shouldReturnListWithInnerFolderIfFolderHasInnerFolder() {
        // Создаем папку верхнего уровня
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        // Создаем вложенную папку
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);

        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Папка должна быть создана в basePath");
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH),
                "В запрашиваемой папке должна быть создана внутренняя папка");

        List<StorageItem> items = minioService.getItems(TEST_USER_PREFIX, FOLDER_0_LVL_PATH);

        assertFalse(items.isEmpty(), "Список не должен быть пустым");
        assertEquals(1, items.size(), "Размер списка элементов должен быть равен 1");
        assertEquals(FOLDER_1_LVL_PATH, items.getFirst().relativePath(), "Должны получить внутреннюю папку");
    }

    @Test
    @DisplayName("Попытка создания вложенной папки в несуществующей папке")
    void shouldThrowExceptionWhenPathForNewPathDoesNotExist() {
        assertFalse(folderExists(TEST_USER_PREFIX + NON_EXISTENT_FOLDER_PATH),
                "Промежуточная папка не должна существовать");

        assertThrows(FolderNotFoundException.class,
                () -> minioService.createFolder(TEST_USER_PREFIX, NON_EXISTENT_FOLDER_PATH, FOLDER_1_LVL_NAME),
                "Должно выбрасываться исключение при попытке создать папку в несуществующей папке");
    }

    @Test
    @DisplayName("Попытка создания папки, которая уже существует")
    void shouldThrowExceptionWhenFolderAlreadyExists() {
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Папка должна быть создана");

        assertThrows(FolderAlreadyExistsException.class,
                () -> minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME),
                "Должно выбрасываться исключение при попытке создать существующую папку");
    }

    @Test
    @DisplayName("Попытка создания папки со значением null для имени")
    void shouldThrowExceptionWhenFolderNameIsNull() {

        String nullFolderName = null;

        assertThrows(IllegalArgumentException.class,
                () -> minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, nullFolderName),
                "Должно выбрасываться исключение при попытке создать папку со значением null для имени");
    }

    @Test
    @DisplayName("Попытка создания папки с пустым именем")
    void shouldThrowExceptionWhenFolderNameIsEmpty() {

        String folderWithEmptyName = "";

        assertThrows(IllegalArgumentException.class,
                () -> minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, folderWithEmptyName),
                "Должно выбрасываться исключение при попытке создать папку с пустым именем");
    }

    @Test
    @DisplayName("Удаление пустой папки")
    void shouldDeleteEmptyFolder() {
        // Создаем папку
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Папка для удаления должна существовать");

        minioService.deleteFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH),
                "Папка не должна существовать после удаления");
    }

    @Test
    @DisplayName("Удаление папки с вложенными объектами")
    void shouldDeleteFolderWithNestedObjects() {
        // Создаем папку и вложенные объекты
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH , FOLDER_1_LVL_NAME);
        // TODO Здесь можно добавить файлы, если нужно

        // Убедимся, что папка и вложенные объекты существуют
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Папка для удаления должна существовать");
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH),
                "Папка вложенная в папку для удаления должна существовать");

        // Удаляем папку содержащую вложенные объекты
        minioService.deleteFolder(TEST_USER_PREFIX, "", FOLDER_0_LVL_NAME);

        // Проверяем, что папка и вложенные объекты удалены
        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Папка не должна существовать, после удаления");
        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH),
                "Папка вложенная в удаленную папку не должна существовать, после удаления родительской папки");
    }

    @Test
    @DisplayName("Удаление папки с двумя уровнями вложенности")
    void shouldDeleteFolderWithTwoLevelsOfNesting() {
        // Создаем папку и вложенные объекты
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_1_LVL_PATH, FOLDER_2_LVL_NAME);

        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Папка для удаления должна существовать");
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH ),
                "Папка вложенная в папку для удаления должна существовать");
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_2_LVL_PATH),
                "Папка вложенная на два уровня в папку для удаления должна существовать");

        // Удаляем папку верхнего уровня
        minioService.deleteFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Папка не должна существовать, после удаления");
        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH),
                "Папка вложенная в удаленную папку не должна существовать, после удаления родительской папки");
        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_2_LVL_PATH),
                "Папка вложенная на два уровня в удаленную папку не должна существовать, после удаления папки верхнего уровня");
    }

    @Test
    @DisplayName("Удаление вложенной папки и проверка существования родительской папки")
    void shouldDeleteNestedFolderButKeepParentFolder() {
        // Создаем папку и вложенные объекты
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_1_LVL_PATH, FOLDER_2_LVL_NAME);

        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Папка должна существовать");
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH), "Папка должна существовать");
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_2_LVL_PATH), "Папка должна существовать");

        // Удаляем вложенную папку первого уровня
        minioService.deleteFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);

        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH), "Папка не должна существовать после удаления");
        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_2_LVL_PATH), "Папка не должна существовать после удаления родительской папки");

        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH), "Родительская папка должна существовать после удаления вложенных");
    }

    @Test
    @DisplayName("Попытка удаления несуществующей папки")
    void shouldThrowExceptionWhenDeletingNonExistentFolder() {
        assertFalse(folderExists(TEST_USER_PREFIX + NON_EXISTENT_FOLDER_PATH),
                "Папка для удаления не должна существовать");

        assertThrows(FolderNotFoundException.class,
                () -> minioService.deleteFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, NON_EXISTENT_FOLDER_NAME),
                "Должно выбрасываться исключение при попытке удаления несуществующей папки");
    }

    @Test
    @DisplayName("Переименование пустой папки в корневой папке пользователя")
    void shouldRenameFolderWithEmptyPath() {
        String renamedFolderName = "renamedFolder";
        String renamedFolderPath = "/" + renamedFolderName + "/";

        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        minioService.renameFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME, renamedFolderName);

        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH),
                "Папка не должна существовать после её переименования");

        assertTrue(folderExists(TEST_USER_PREFIX + renamedFolderPath),
                "Папка должна существовать с новым именем, после переименования");
    }

    @Test
    @DisplayName("Переименование пустой вложенной папки")
    void shouldRenameEmptyFolder() {
        String renamedFolderName = "renamedEmptyFolder";
        String renamedFolderPath = FOLDER_0_LVL_PATH + renamedFolderName + "/";

        // Создаем родительскую папку и пустую вложенную папку
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);

        minioService.renameFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME, renamedFolderName);

        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH),
                "Папка не должна существовать после её переименования");

        assertTrue(folderExists(TEST_USER_PREFIX + renamedFolderPath),
                "Папка должна существовать с новым именем, после переименования");
    }

    @Test
    @DisplayName("Попытка переименования несуществующей папки")
    void shouldThrowExceptionWhenRenamingNonExistentFolder() {

        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        assertThrows(FolderNotFoundException.class,
                () -> minioService.renameFolder(
                        TEST_USER_PREFIX, FOLDER_0_LVL_PATH, NON_EXISTENT_FOLDER_NAME, "renamedFolderName"),
                "Должно выбрасываться исключение при попытке переименования несуществующей папки");
    }

    @Test
    @DisplayName("Попытка переименования папки в уже существующую папку")
    void shouldThrowExceptionWhenRenamingToExistingFolder() {
        String existingFolderName = "existingFolder";

        // Создаем родительскую папку и две вложенные папки
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, existingFolderName);

        assertThrows(FolderAlreadyExistsException.class,
                () -> minioService.renameFolder(
                        TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME, existingFolderName),
                "Должно выбрасываться исключение" +
                        " при попытке переименования с названием уже существующей папки");
    }

    @Test
    @DisplayName("Переименование папки с вложенными объектами")
    void shouldRenameFolderWithNestedFolders() {
        String renamedFolderName = "renamed-folder";
        String renamedFolderPath = FOLDER_0_LVL_PATH + renamedFolderName + "/";

        // Создаем папку и вложенные объекты
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME);
        minioService.createFolder(TEST_USER_PREFIX, FOLDER_1_LVL_PATH, FOLDER_2_LVL_NAME);
        // minioService.uploadFile(); // TODO добавить файлы

        // Переименовываем папку с одним уровнем вложенности
        minioService.renameFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, FOLDER_1_LVL_NAME, renamedFolderName);

        // Проверяем, что старые объекты удалены
        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH),
                "Папка не должна существовать после её переименовании");
        assertFalse(folderExists(TEST_USER_PREFIX + FOLDER_2_LVL_PATH),
                "После переименования родительской папки " +
                        "вложенная папка не должна существовать по первоначальному пути");
        // assertFalse(fileExists());

        assertTrue(folderExists(TEST_USER_PREFIX + renamedFolderPath),
                "Папка должна существовать с новым именем, после переименования");
        assertTrue(folderExists(TEST_USER_PREFIX + renamedFolderPath + FOLDER_2_LVL_NAME + "/"),
                "После переименования родительской папки вложенная папка должна существовать по новому пути");
        // assertTrue(fileExists());
    }

    @Test
    @DisplayName("Загрузка файла в корневую папку пользователя")
    void shouldUploadFileToBaseFolder() {
        minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, MULTIPART_TEST_FILE);

        String fullFilePath = TEST_USER_PREFIX + "/" + TEST_FILE_NAME;
        assertTrue(fileExists(fullFilePath), "Файл должен быть загружен в корневую папку пользователя");
    }

    @Test
    @DisplayName("Загрузка файла во вложенную папку")
    void shouldUploadFileToNestedFolder() {
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        minioService.uploadFile(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, MULTIPART_TEST_FILE);

        String fullFilePath = TEST_USER_PREFIX + FOLDER_0_LVL_PATH + TEST_FILE_NAME;
        assertTrue(fileExists(fullFilePath), "Файл должен быть загружен во вложенную папку");
    }

    @Test
    @DisplayName("Попытка загрузки файла, который уже существует")
    void shouldThrowExceptionWhenUploadingExistingFile() {
        // Загружаем файл в первый раз
        minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, MULTIPART_TEST_FILE);

        // Проверяем, что файл загружен
        String fullFilePath = TEST_USER_PREFIX + "/" + TEST_FILE_NAME;
        assertTrue(fileExists(fullFilePath), "Файл должен быть загружен в MinIO");

        // Пытаемся загрузить файл с тем же именем
        assertThrows(FileAlreadyExistsInStorageException.class,
                () -> minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, MULTIPART_TEST_FILE),
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
                MULTIPART_TEST_FILE.getBytes()
        );

        // Пытаемся загрузить файл с пустым именем
        assertThrows(IllegalArgumentException.class,
                () -> minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, emptyNameFile),
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
                MULTIPART_TEST_FILE.getBytes()
        );

        // Пытаемся загрузить файл с некорректным именем
        Exception exception = assertThrows(RuntimeException.class,
                () -> minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, invalidNameFile),
                "Должно выбрасываться исключение при попытке загрузить файл с некорректным именем");

        // Проверяем, что причина исключения - IllegalArgumentException
        assertInstanceOf(IllegalArgumentException.class, exception.getCause(),
                "Причина исключения должна быть IllegalArgumentException");
    }

    @Test
    @DisplayName("Удаление существующего файла")
    void shouldDeleteExistingFile() {
        minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, MULTIPART_TEST_FILE);

        String fullFilePath = TEST_USER_PREFIX + "/" + TEST_FILE_NAME;
        assertTrue(fileExists(fullFilePath), "Файл должен существовать в корневой папке пользователя");

        minioService.deleteFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, TEST_FILE_NAME);

        assertFalse(fileExists(fullFilePath), "Файл не должен существовать после удаления");
    }

    @Test
    @DisplayName("Попытка удаления несуществующего файла")
    void shouldThrowExceptionWhenDeletingNonExistentFile() {
        String nonExistentFileName = "non-existent-file.txt";

        assertFalse(fileExists(TEST_USER_PREFIX + "/" + nonExistentFileName), "Файл не должен существовать в MinIO");

        // Пытаемся удалить несуществующий файл
        assertThrows(FileNotFoundInStorageException.class,
                () -> minioService.deleteFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, nonExistentFileName),
                "Должно выбрасываться исключение при попытке удалить несуществующий файл");
    }

    @Test
    @DisplayName("Попытка удаления файла с пустым именем")
    void shouldThrowExceptionWhenDeletingFileWithEmptyName() {
        String emptyFileName = "";

        assertThrows(IllegalArgumentException.class,
                () -> minioService.deleteFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, emptyFileName),
                "Должно выбрасываться исключение при попытке удалить файл с пустым именем");
    }

    @Test
    @DisplayName("Удаление файла из вложенной папки")
    void shouldDeleteFileFromNestedFolder() {
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        minioService.uploadFile(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, MULTIPART_TEST_FILE);

        String fullFilePath = TEST_USER_PREFIX + FOLDER_0_LVL_PATH + TEST_FILE_NAME;
        assertTrue(fileExists(fullFilePath), "Файл должен существовать во вложенной папке");

        minioService.deleteFile(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, TEST_FILE_NAME);

        assertFalse(fileExists(fullFilePath), "Файл не должен существовать после удаления");
    }

    @Test
    @DisplayName("Попытка удаления файла с некорректным именем")
    void shouldThrowExceptionWhenDeletingFileWithInvalidName() {
        String invalidFileName = "../invalid-file.txt";

        // Пытаемся удалить файл с некорректным именем
        Exception exception = assertThrows(RuntimeException.class,
                () -> minioService.deleteFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, invalidFileName),
                "Должно выбрасываться исключение при попытке удалить файл с некорректным именем");

        // Проверяем, что причина исключения - IllegalArgumentException
        assertInstanceOf(IllegalArgumentException.class, exception.getCause(),
                "Причина исключения должна быть IllegalArgumentException");
    }

    @Test
    @DisplayName("Переименование существующего файла")
    void shouldRenameExistingFile() {
        minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, MULTIPART_TEST_FILE);

        String fullOldFilePath = TEST_USER_PREFIX + "/" + TEST_FILE_NAME;
        assertTrue(fileExists(fullOldFilePath), "Файл должен существовать в корневой папке пользователя");

        minioService.renameFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, TEST_FILE_NAME, RENAMED_FILE_NAME);

        String fullNewFilePath = TEST_USER_PREFIX + "/" + RENAMED_FILE_NAME;
        assertTrue(fileExists(fullNewFilePath), "Файл c новым названием должен существовать");
        assertFalse(fileExists(fullOldFilePath), "Файл c изначальным названием не должен существовать");
    }

    @Test
    @DisplayName("Попытка переименования несуществующего файла")
    void shouldThrowExceptionWhenRenamingNonExistentFile() {
        String nonExistentFileName = "non-existent-file.txt";

        assertFalse(fileExists(TEST_USER_PREFIX + "/" + nonExistentFileName), "Файл не должен существовать в MinIO");

        assertThrows(FileNotFoundInStorageException.class,
                () -> minioService.renameFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, nonExistentFileName, RENAMED_FILE_NAME),
                "Должно выбрасываться исключение при попытке переименовать несуществующий файл");
    }

    @Test
    @DisplayName("Попытка переименования файла в уже существующий файл")
    void shouldThrowExceptionWhenRenamingToExistingFile() {
        String existingFileName = "test-existing-file.txt";

        minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, MULTIPART_TEST_FILE);
        minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, new MockMultipartFile(
                "existing-file",
                existingFileName,
                "text/plain",
                "Hello, World!".getBytes()
        ));

        assertTrue(fileExists(TEST_USER_PREFIX + "/" + TEST_FILE_NAME),
                "Файл должен существовать в корневой папке пользователя");
        assertTrue(fileExists(TEST_USER_PREFIX + "/" + existingFileName),
                "Файл должен существовать в корневой папке пользователя");

        assertThrows(FileAlreadyExistsInStorageException.class,
                () -> minioService.renameFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, TEST_FILE_NAME, existingFileName),
                "Должно выбрасываться исключение при попытке переименовать файл в уже существующий файл");
    }

    @Test
    @DisplayName("Попытка переименования файла с передачей пустого имени в параметр")
    void shouldThrowExceptionWhenRenamingFileWithEmptyName() {
        String emptyFileName = "";

        assertThrows(IllegalArgumentException.class,
                () -> minioService.renameFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, emptyFileName, RENAMED_FILE_NAME),
                "Должно выбрасываться исключение при попытке переименовать файл с передачей пустого имени в параметр");
    }

    @Test
    @DisplayName("Попытка переименования файла с пустым новым именем")
    void shouldThrowExceptionWhenRenamingFileWithEmptyNewName() {
        minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, MULTIPART_TEST_FILE);

        String fullOldPath = TEST_USER_PREFIX + "/" + TEST_FILE_NAME;
        assertTrue(fileExists(fullOldPath), "Файл должен существовать в корневой папке пользователя");

        String emptyNewFileName = "";

        assertThrows(IllegalArgumentException.class,
                () -> minioService.renameFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, TEST_FILE_NAME, emptyNewFileName),
                "Должно выбрасываться исключение при попытке переименовать файл с пустым новым именем");
    }

    @Test
    @DisplayName("Переименование файла во вложенной папке")
    void shouldRenameFileInNestedFolder() {
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        minioService.uploadFile(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, MULTIPART_TEST_FILE);

        String fullTestFilePath = TEST_USER_PREFIX + FOLDER_0_LVL_PATH + TEST_FILE_NAME;
        assertTrue(fileExists(fullTestFilePath), "Файл должен существовать во вложенной папке");

        minioService.renameFile(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, TEST_FILE_NAME, RENAMED_FILE_NAME);

        String fullRenamedFilePath = TEST_USER_PREFIX + FOLDER_0_LVL_PATH + RENAMED_FILE_NAME;
        assertTrue(fileExists(fullRenamedFilePath),
                "Переименованный файл должен существовать в той же папке с новым именем");
        assertFalse(fileExists(fullTestFilePath),
                "Файл с первоначальным именем не должен существовать после переименования");
    }

    @Test
    @DisplayName("Загрузка папки в корневую папку пользователя")
    void shouldUploadFolderToRoot() {
        String testFileName = "file1.txt";
        String testFilePath = FOLDER_0_LVL_NAME + "/" + testFileName;
        MultipartFile multipartTestFile = new MockMultipartFile(
                testFileName,
                testFilePath,
                "text/plain",
                "Hello, World!".getBytes()
        );
        String fileInNestedFolderName = "file2.txt";
        String fileInNestedFolderPath = FOLDER_0_LVL_NAME + "/" + FOLDER_1_LVL_NAME + "/" + fileInNestedFolderName;
        MultipartFile nestedMultipartFile = new MockMultipartFile(
                fileInNestedFolderName,
                fileInNestedFolderPath,
                "text/plain",
                "Hello2".getBytes()
        );

        // Загружаем папку в корневую папку пользователя
        minioService.uploadFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH,
                FOLDER_0_LVL_NAME, new MultipartFile[]{multipartTestFile, nestedMultipartFile});

        // Проверяем, что папка создана
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH),
                "Папка c именем загружаемой папки должна существовать в корневой папке пользователя");

        // Проверяем, что файл загружен
        String fullFilePath = TEST_USER_PREFIX + FOLDER_0_LVL_PATH + testFileName;
        assertTrue(fileExists(fullFilePath), "Тестовый файл должен существовать внутри папки, которая была загружена");

        // Проверяем, что вложенная папка создана
        assertTrue(folderExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH),
                "Папка, вложенная в загружаемую папку, должна быть создана");

        // Проверяем, что файл file2.txt загружен
        assertTrue(fileExists(TEST_USER_PREFIX + FOLDER_1_LVL_PATH + fileInNestedFolderName),
                "Тестовый файл должен существовать внутри папки, которая была вложена в загруженную папку ");
    }

    @Test
    @DisplayName("Загрузка папки во вложенную папку")
    void shouldUploadFolderToNestedFolder() {
        // Создаем тестовые файлы
        String folderName = "uploaded-folder";
        String file1Name = "file1.txt";
        String file1Ptah = "uploaded-folder/file1.txt";
        String subFolderName = "sub-folder";
        String file2Name = "file2.txt";
        String file2Path = "uploaded-folder/sub-folder/file2.txt";

        // Создаем MultipartFile
        MultipartFile multipartFile1 = new MockMultipartFile(
                file1Name,
                file1Ptah,
                "text/plain",
                "Hello, World!".getBytes()
        );

        MultipartFile multipartFile2 = new MockMultipartFile(
                file2Name,
                file2Path,
                "text/plain",
                "Hello2".getBytes()
        );

        // Создаем вложенную папку в MinIO
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);

        // Загружаем папку во вложенную папку
        minioService.uploadFolder(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, folderName, new MultipartFile[]{multipartFile1, multipartFile2});

        // Проверяем, что папка создана
        String fullFolderPath = TEST_USER_PREFIX + FOLDER_0_LVL_PATH + folderName + "/";
        assertTrue(folderExists(fullFolderPath), "Папка должна быть создана");

        // Проверяем, что файл file1.txt загружен
        String fullFilePath1 = fullFolderPath + file1Name;
        assertTrue(fileExists(fullFilePath1), "Файл file1.txt должен быть загружен");

        // Проверяем, что вложенная папка sub-folder создана
        String fullSubFolderPath = fullFolderPath + subFolderName;
        assertTrue(folderExists(fullSubFolderPath), "Вложенная папка sub-folder должна быть создана");

        // Проверяем, что файл file2.txt загружен
        String fullFilePath2 = fullFolderPath + subFolderName + "/" + file2Name;
        assertTrue(fileExists(fullFilePath2), "Файл file2.txt должен быть загружен");
    }

    @Test
    @DisplayName("Попытка загрузки папки в несуществующую папку")
    void shouldThrowExceptionWhenUploadingFolderToNonExistentFolder() {
        // Создаем тестовые файлы
        String folderName = "uploaded-folder";
        String file1Name = "file1.txt";
        String file1Ptah = "uploaded-folder/file1.txt";

        // Создаем MultipartFile
        MultipartFile multipartFile1 = new MockMultipartFile(
                file1Name,
                file1Ptah,
                "text/plain",
                "Hello, World!".getBytes()
        );

        assertThrows(FolderNotFoundException.class,
                () -> minioService.uploadFolder(TEST_USER_PREFIX, NON_EXISTENT_FOLDER_PATH, folderName, new MultipartFile[]{multipartFile1}),
                "Должно выбрасываться исключение при попытке загрузить папку в несуществующую папку");
    }

    @Test
    @DisplayName("Попытка загрузки папки с уже существующим именем")
    void shouldThrowExceptionWhenUploadingFolderWithExistingName(){
        // Создаем тестовые файлы
        String folderName = "uploaded-folder";
        String file1Name = "file1.txt";
        String file1Ptah = "uploaded-folder/file1.txt";

        // Создаем MultipartFile
        MultipartFile multipartFile1 = new MockMultipartFile(
                file1Name,
                file1Ptah,
                "text/plain",
                "Hello, World!".getBytes()
        );

        // Создаем папку с таким же именем
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, folderName);

        assertThrows(FolderAlreadyExistsException.class,
                () -> minioService.uploadFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, folderName, new MultipartFile[]{multipartFile1}),
                "Должно выбрасываться исключение при попытке загрузить папку с уже существующим именем");
    }

    @Test
    @DisplayName("Успешное скачивание файла из корневой папки пользователя")
    void shouldDownloadFileFromRootFolderSuccessfully() throws Exception {
        minioService.uploadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, MULTIPART_TEST_FILE);
        String fullFilePath = TEST_USER_PREFIX + "/" + TEST_FILE_NAME;
        assertTrue(fileExists(fullFilePath), "Файл должен существовать в корневой папке пользователя");

        InputStreamResource result = minioService.downloadFile(TEST_USER_PREFIX, ROOT_FOLDER_PATH, TEST_FILE_NAME);

        assertNotNull(result);
        assertEquals(TEST_FILE_CONTENT, new String(result.getInputStream().readAllBytes()));
    }

    @Test
    @DisplayName("Успешное скачивание файла из вложенной папки")
    void shouldDownloadFileFromNestedFolderSuccessfully() throws Exception {
        minioService.createFolder(TEST_USER_PREFIX, ROOT_FOLDER_PATH, FOLDER_0_LVL_NAME);
        assertTrue(fileExists(TEST_USER_PREFIX + FOLDER_0_LVL_PATH),
                "Папка должна существовать в корневой папке пользователя");
        minioService.uploadFile(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, MULTIPART_TEST_FILE);
        String fullFilePath = TEST_USER_PREFIX + FOLDER_0_LVL_PATH + TEST_FILE_NAME;
        assertTrue(fileExists(fullFilePath), "Файл должен существовать во вложенной папке");

        InputStreamResource result = minioService.downloadFile(TEST_USER_PREFIX, FOLDER_0_LVL_PATH, TEST_FILE_NAME);

        assertNotNull(result);
        assertEquals(TEST_FILE_CONTENT, new String(result.getInputStream().readAllBytes()));
    }

    @Test
    @DisplayName("Проверка выкидывания исключения при попытке скачивании несуществующего файла")
    void shouldThrowExceptionWhenDownloadingNonExistentFile() {
        String folderPath = "/documents/";
        String fileName = "report.pdf";
        assertFalse(fileExists(TEST_USER_PREFIX + folderPath + fileName), "Файл не должен существовать");

        assertThrows(
                FileNotFoundInStorageException.class,
                () -> minioService.downloadFile(TEST_USER_PREFIX, folderPath, fileName),
                "Должно выбрасываться исключение при попытке скачивании несуществующего файла"
        );
    }

    @Test
    @DisplayName("Успешное скачивание папки в виде ZIP-архива")
    void shouldDownloadFolderAsZipSuccessfully() throws Exception {
        String folderPath = "/documents/";
        String folderName = "my-folder";
        String fullFolderPath = TEST_USER_PREFIX + folderPath + folderName + "/";

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
                        .object(TEST_USER_PREFIX + folderPath)
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

        InputStreamResource result = minioService.downloadFolder(TEST_USER_PREFIX, folderPath, folderName);

        assertNotNull(result, "Результат скачивания папки с контентом не должен быть пустым");

        // Проверяем содержимое ZIP-архива
        try (ZipInputStream zipIn = new ZipInputStream(result.getInputStream())) {
            ZipEntry entry;
            Map<String, String> files = new HashMap<>();
            while ((entry = zipIn.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zipIn.readAllBytes()));
            }

            assertEquals(3, files.size());
            assertEquals("file1 content", files.get("file1.txt"));
            assertEquals("file2 content", files.get("sub-folder/file2.txt"));
        }
    }

    @Test
    @DisplayName("Попытка скачивания несуществующей папки")
    void shouldThrowExceptionWhenFolderNotFound() {
        String folderPath = "/documents/";
        String folderName = "non-existent-folder";

        assertThrows(
                FolderNotFoundException.class,
                () -> minioService.downloadFolder(TEST_USER_PREFIX, folderPath, folderName)
        );
    }

    @Test
    @DisplayName("Скачивание папки с файлами, содержащими специальные символы в именах")
    void shouldDownloadFolderWithSpecialCharactersInFileNames() throws Exception {
        // Arrange
        String folderPath = "/documents/";
        String folderName = "special-chars-folder";
        String fullFolderPath = TEST_USER_PREFIX + folderPath + folderName + "/";

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
        InputStreamResource result = minioService.downloadFolder(TEST_USER_PREFIX, folderPath, folderName);

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
        String query = "мокр";

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(TEST_USER_PREFIX + "/мокрые/")
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(TEST_USER_PREFIX + "/мокрые/мокрый зонт.jpg")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(TEST_USER_PREFIX + "мокрые/дырочки.jpg")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );

        List<StorageItem> results = minioService.searchItems(TEST_USER_PREFIX, query);

        assertEquals(2, results.size());
        assertEquals("/мокрые/", results.get(0).relativePath());
        assertEquals("/мокрые/мокрый зонт.jpg", results.get(1).relativePath());
    }

    @Test
    @DisplayName("Поиск файлов в корневой папке пользователя")
    void shouldSearchFilesInRootFolder() throws Exception {
        String query = "file";

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(TEST_USER_PREFIX + "/file1.txt")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(TEST_USER_PREFIX + "/file2.txt")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(TEST_USER_PREFIX + "/image.jpg")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );

        List<StorageItem> results = minioService.searchItems(TEST_USER_PREFIX, query);

        assertEquals(2, results.size());
        assertEquals("/file1.txt", results.get(0).relativePath());
        assertEquals("/file2.txt", results.get(1).relativePath());
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
                        .object(TEST_USER_PREFIX + "/file1.txt")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(TEST_USER_PREFIX + "/File2.txt")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(TEST_USER_PREFIX + "/image.jpg")
                        .stream(new ByteArrayInputStream("content".getBytes()), 7, -1)
                        .build()
        );

        // Act
        List<StorageItem> results = minioService.searchItems(TEST_USER_PREFIX, query);

        // Assert
        assertEquals(2, results.size());
        assertEquals("/File2.txt", results.get(0).relativePath());
        assertEquals("/file1.txt", results.get(1).relativePath());
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
                            .prefix(TEST_USER_PREFIX)
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
