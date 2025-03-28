package ru.vladshi.cloudfilestorage.storage.service;

import io.minio.PutObjectArgs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.storage.exception.StorageException;
import ru.vladshi.cloudfilestorage.storage.service.impl.MinioClientProvider;
import ru.vladshi.cloudfilestorage.storage.service.impl.MinioFileServiceImpl;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {MinioFileServiceImpl.class, MinioClientProvider.class,
                AbstractMinioServiceTest.MinioClientConfig.class},
        properties = {
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        })
public class MinioFileServiceImplTest extends AbstractMinioServiceTest {

    private static final String NEW_TEST_FILE_NAME = "new-test-file.txt";

    @Autowired
    private FileService fileService;

    @Test
    @DisplayName("Загрузка файла в корневую папку пользователя")
    void shouldUploadFileToRootFolder() throws Exception {

        fileService.upload(ROOT_USER_FOLDER, MULTIPART_TEST_FILE);

        assertTrue(fileExists(ROOT_USER_FOLDER + TEST_FILE_NAME), "Файл должен быть загружен в корневую папку");
    }

    @Test
    @DisplayName("Загрузка файла во вложенную папку")
    void shouldUploadFileToNestedFolder() throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER)
                .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                .build());

        fileService.upload(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, MULTIPART_TEST_FILE);

        assertTrue(fileExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + TEST_FILE_NAME),
                "Файл должен быть загружен во вложенную папку");
    }

    @Test
    @DisplayName("Попытка загрузки уже существующего файла")
    void shouldThrowExceptionWhenUploadingExistingFile() throws Exception {
        fileService.upload(ROOT_USER_FOLDER, MULTIPART_TEST_FILE);

        assertThrows(StorageException.class,
                () -> fileService.upload(ROOT_USER_FOLDER, MULTIPART_TEST_FILE),
                "Должно выбросить исключение при загрузке уже существующего файла");
    }

    @Test
    @DisplayName("Попытка загрузки файла с пустым именем")
    void shouldThrowExceptionWhenUploadingFileWithEmptyName() {
        MultipartFile mockFile = new MockMultipartFile("file", "", "text/plain", HELLO_MINIO_BYTES);

        assertThrows(StorageException.class,
                () -> fileService.upload(ROOT_USER_FOLDER, mockFile),
                "Должно выбросить исключение при загрузке файла с пустым именем");
    }

    @Test
    @DisplayName("Удаление существующего файла")
    void shouldDeleteExistingFile() throws Exception {
        fileService.upload(ROOT_USER_FOLDER, MULTIPART_TEST_FILE);

        fileService.delete(ROOT_USER_FOLDER, TEST_FILE_NAME);

        assertFalse(fileExists(ROOT_USER_FOLDER + TEST_FILE_NAME), "Файл должен быть удален");
    }

    @Test
    @DisplayName("Попытка удаления несуществующего файла")
    void shouldThrowExceptionWhenDeletingNonExistentFile() {
        assertThrows(StorageException.class,
                () -> fileService.delete(ROOT_USER_FOLDER, "non-existent.txt"),
                "Должно выбросить исключение при удалении несуществующего файла");
    }

    @Test
    @DisplayName("Попытка удаления файла с пустым именем")
    void shouldThrowExceptionWhenDeletingFileWithEmptyName() {
        assertThrows(StorageException.class,
                () -> fileService.delete(ROOT_USER_FOLDER, ""),
                "Должно выбросить исключение при удалении файла с пустым именем");
    }

    @Test
    @DisplayName("Удаление файла из вложенной папки")
    void shouldDeleteFileFromNestedFolder() throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER)
                .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                .build());
        fileService.upload(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, MULTIPART_TEST_FILE);

        fileService.delete(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, TEST_FILE_NAME);

        assertFalse(fileExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + TEST_FILE_NAME),
                "Файл должен быть удален из вложенной папки");
    }

    @Test
    @DisplayName("Переименование существующего файла")
    void shouldRenameExistingFile() throws Exception {
        fileService.upload(ROOT_USER_FOLDER, MULTIPART_TEST_FILE);

        fileService.rename(ROOT_USER_FOLDER, TEST_FILE_NAME, NEW_TEST_FILE_NAME);

        assertFalse(fileExists(ROOT_USER_FOLDER + TEST_FILE_NAME), "Старое имя файла должно исчезнуть");
        assertTrue(fileExists(ROOT_USER_FOLDER + NEW_TEST_FILE_NAME), "Новое имя файла должно появиться");
    }

    @Test
    @DisplayName("Попытка переименования несуществующего файла")
    void shouldThrowExceptionWhenRenamingNonExistentFile() {
        assertThrows(StorageException.class,
                () -> fileService.rename(ROOT_USER_FOLDER, "non-existent.txt", NEW_TEST_FILE_NAME),
                "Должно выбросить исключение при переименовании несуществующего файла");
    }

    @Test
    @DisplayName("Попытка переименования файла в уже существующий файл")
    void shouldThrowExceptionWhenRenamingToExistingFile() throws Exception {
        fileService.upload(ROOT_USER_FOLDER, MULTIPART_TEST_FILE);
        MultipartFile newFile = new MockMultipartFile("file", NEW_TEST_FILE_NAME, "text/plain", HELLO_MINIO_BYTES);
        fileService.upload(ROOT_USER_FOLDER, newFile);

        assertThrows(StorageException.class,
                () -> fileService.rename(ROOT_USER_FOLDER, TEST_FILE_NAME, NEW_TEST_FILE_NAME),
                "Должно выбросить исключение при переименовании в существующий файл");
    }

    @Test
    @DisplayName("Попытка переименования файла с пустым старым именем")
    void shouldThrowExceptionWhenRenamingFileWithEmptyOldName() {
        assertThrows(StorageException.class,
                () -> fileService.rename(ROOT_USER_FOLDER, "", NEW_TEST_FILE_NAME),
                "Должно выбросить исключение при переименовании с пустым старым именем");
    }

    @Test
    @DisplayName("Попытка переименования файла с пустым новым именем")
    void shouldThrowExceptionWhenRenamingFileWithEmptyNewName() throws Exception {
        fileService.upload(ROOT_USER_FOLDER, MULTIPART_TEST_FILE);
        assertThrows(StorageException.class,
                () -> fileService.rename(ROOT_USER_FOLDER, TEST_FILE_NAME, ""),
                "Должно выбросить исключение при переименовании с пустым новым именем");
    }

    @Test
    @DisplayName("Переименование файла во вложенной папке")
    void shouldRenameFileInNestedFolder() throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER)
                .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                .build());
        fileService.upload(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, MULTIPART_TEST_FILE);

        fileService.rename(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, TEST_FILE_NAME, NEW_TEST_FILE_NAME);

        assertFalse(fileExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + TEST_FILE_NAME),
                "Старое имя файла должно исчезнуть");
        assertTrue(fileExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + NEW_TEST_FILE_NAME),
                "Новое имя файла должно появиться");
    }

    @Test
    @DisplayName("Скачивание файла из корневой папки пользователя")
    void shouldDownloadFileFromRootFolder() throws Exception {
        fileService.upload(ROOT_USER_FOLDER, MULTIPART_TEST_FILE);

        InputStreamResource resource = fileService.download(ROOT_USER_FOLDER, TEST_FILE_NAME);

        assertNotNull(resource, "Ресурс файла не должен быть null");
    }

    @Test
    @DisplayName("Скачивание файла из вложенной папки")
    void shouldDownloadFileFromNestedFolder() throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER)
                .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                .build());
        fileService.upload(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, MULTIPART_TEST_FILE);

        InputStreamResource resource = fileService.download(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, TEST_FILE_NAME);

        assertNotNull(resource, "Ресурс файла не должен быть null");
    }

    @Test
    @DisplayName("Попытка скачивания несуществующего файла")
    void shouldThrowExceptionWhenDownloadingNonExistentFile() {
        assertThrows(StorageException.class,
                () -> fileService.download(ROOT_USER_FOLDER, "non-existent.txt"),
                "Должно выбросить исключение при скачивании несуществующего файла");
    }

    @Test
    @DisplayName("Получение размера файла из хранилища")
    void shouldGetFileSizeFromStorage() throws Exception {
        fileService.upload(ROOT_USER_FOLDER, MULTIPART_TEST_FILE);

        long size = fileService.getFileSize(ROOT_USER_FOLDER, TEST_FILE_NAME);

        assertEquals(HELLO_MINIO_BYTES.length, size, "Размер файла должен соответствовать загруженному");
    }
}
