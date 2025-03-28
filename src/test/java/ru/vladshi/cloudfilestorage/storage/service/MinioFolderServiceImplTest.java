package ru.vladshi.cloudfilestorage.storage.service;

import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import ru.vladshi.cloudfilestorage.storage.exception.FolderNotFoundException;
import ru.vladshi.cloudfilestorage.storage.exception.StorageException;
import ru.vladshi.cloudfilestorage.storage.model.StorageItem;
import ru.vladshi.cloudfilestorage.storage.service.impl.MinioClientProvider;
import ru.vladshi.cloudfilestorage.storage.service.impl.MinioFolderServiceImpl;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = { MinioFolderServiceImpl.class, MinioClientProvider.class,
                AbstractMinioServiceTest.MinioClientConfig.class},
        properties = {
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
public class MinioFolderServiceImplTest extends AbstractMinioServiceTest {

    private static final String SECOND_LEVEL_FOLDER = FIRST_LEVEL_FOLDER + "second-folder/";

    @Autowired
    private FolderService folderService;

    @Test
    @DisplayName("Создание новой пользовательской папки в MinIO")
    void shouldCreateUserRootFolderSuccessfully() throws Exception {
        String newFolderName = "2-new_user/";

        folderService.createUserRootFolder(newFolderName);

        assertTrue(folderExists(newFolderName), "Новая папка должна быть создана в MinIO");

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(TEST_BUCKET_NAME)
                        .object(newFolderName)
                        .build()
        );
    }

    @Test
    @DisplayName("Получение содержимого пустой корневой папки пользователя")
    void shouldReturnEmptyListForEmptyRootFolder() throws Exception {

        List<StorageItem> contents = folderService.getFolderContents(ROOT_USER_FOLDER);

        assertNotNull(contents, "Список содержимого не должен быть null");
        assertTrue(contents.isEmpty(), "Список содержимого должен быть пустым для пустой папки");
    }

    @Test
    @DisplayName("Создание папки в корневой папке пользователя")
    void shouldCreateFolderInRootFolder() throws Exception {

        folderService.create(ROOT_USER_FOLDER, "new-folder");

        assertTrue(folderExists(ROOT_USER_FOLDER + "new-folder/"), "Папка должна быть создана в корневой папке");
    }

    @Test
    @DisplayName("Получение содержимого пустой вложенной папки")
    void shouldReturnEmptyListForEmptyNestedFolder() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "new-folder");

        List<StorageItem> contents = folderService.getFolderContents(ROOT_USER_FOLDER + "new-folder/");

        assertNotNull(contents, "Список содержимого не должен быть null");
        assertTrue(contents.isEmpty(), "Список содержимого должен быть пустым для пустой папки");
    }

    @Test
    @DisplayName("Получение содержимого несуществующей папки")
    void shouldThrowExceptionWhenGettingContentsOfNonExistentFolder() {
        assertThrows(FolderNotFoundException.class,
                () -> folderService.getFolderContents(ROOT_USER_FOLDER + "non-existent/"),
                "Должно выбросить исключение для несуществующей папки");
    }

    @Test
    @DisplayName("Создание вложенной папки на два уровня")
    void shouldCreateNestedFolderTwoLevelsDeep() throws Exception {

        folderService.create(ROOT_USER_FOLDER, "first-folder");
        folderService.create(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, "second-folder");

        assertTrue(folderExists(ROOT_USER_FOLDER + SECOND_LEVEL_FOLDER),
                "Вложенная папка второго уровня должна быть создана");
    }

    @Test
    @DisplayName("Получение содержимого папки с одной вложенной папкой")
    void shouldReturnNestedFolderInFolderContents() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "first-folder");

        List<StorageItem> contents = folderService.getFolderContents(ROOT_USER_FOLDER);

        assertEquals(1, contents.size(), "Должна быть одна вложенная папка в списке");
        assertEquals(FIRST_LEVEL_FOLDER, contents.getFirst().relativePath(), "Путь должен быть относительным");
        assertTrue(contents.getFirst().isFolder(), "Элемент должен быть папкой");
    }

    @Test
    @DisplayName("Попытка создания папки в несуществующей папке")
    void shouldThrowExceptionWhenCreatingFolderInNonExistentFolder() {
        assertThrows(FolderNotFoundException.class,
                () -> folderService.create(ROOT_USER_FOLDER + "non-existent/", "new-folder"),
                "Должно выбросить исключение при создании в несуществующей папке");
    }

    @Test
    @DisplayName("Попытка создания уже существующей папки")
    void shouldThrowExceptionWhenCreatingExistingFolder() throws Exception {

        folderService.create(ROOT_USER_FOLDER, "new-folder");

        assertThrows(StorageException.class,
                () -> folderService.create(ROOT_USER_FOLDER, "new-folder"),
                "Должно выбросить исключение при создании уже существующей папки");
    }

    @Test
    @DisplayName("Попытка создания папки с именем null")
    void shouldThrowExceptionWhenCreatingFolderWithNullName() {
        assertThrows(StorageException.class,
                () -> folderService.create(ROOT_USER_FOLDER, null),
                "Должно выбросить исключение для имени null");
    }

    @Test
    @DisplayName("Попытка создания папки с пустым именем")
    void shouldThrowExceptionWhenCreatingFolderWithEmptyName() {
        assertThrows(StorageException.class,
                () -> folderService.create(ROOT_USER_FOLDER, ""),
                "Должно выбросить исключение для пустого имени");
    }

    @Test
    @DisplayName("Удаление пустой папки")
    void shouldDeleteEmptyFolder() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "new-folder");

        folderService.delete(ROOT_USER_FOLDER, "new-folder");

        assertFalse(folderExists(ROOT_USER_FOLDER + "new-folder/"), "Папка должна быть удалена");
    }

    @Test
    @DisplayName("Удаление папки с вложенными объектами")
    void shouldDeleteFolderWithNestedObjects() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "first-folder");
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + TEST_FILE_NAME)
                .stream(new ByteArrayInputStream(HELLO_MINIO_BYTES), HELLO_MINIO_BYTES.length, -1)
                .build());

        folderService.delete(ROOT_USER_FOLDER, "first-folder");

        assertFalse(folderExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER), "Папка должна быть удалена");
        assertFalse(fileExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + TEST_FILE_NAME),
                "Вложенный файл должен быть удален");
    }

    @Test
    @DisplayName("Удаление папки с двумя уровнями вложенности")
    void shouldDeleteFolderWithTwoLevelsOfNesting() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "first-folder");
        folderService.create(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, "second-folder");

        folderService.delete(ROOT_USER_FOLDER, "first-folder");

        assertFalse(folderExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER), "Папка первого уровня должна быть удалена");
        assertFalse(folderExists(SECOND_LEVEL_FOLDER), "Папка второго уровня должна быть удалена");
    }

    @Test
    @DisplayName("Удаление вложенной папки с сохранением родительской")
    void shouldDeleteNestedFolderAndKeepParent() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "first-folder");
        folderService.create(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, "second-folder");

        folderService.delete(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, "second-folder");

        assertTrue(folderExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER), "Родительская папка должна остаться");
        assertFalse(folderExists(SECOND_LEVEL_FOLDER), "Вложенная папка должна быть удалена");
    }

    @Test
    @DisplayName("Попытка удаления несуществующей папки")
    void shouldThrowExceptionWhenDeletingNonExistentFolder() {
        assertThrows(FolderNotFoundException.class,
                () -> folderService.delete(ROOT_USER_FOLDER, "non-existent"),
                "Должно выбросить исключение при удалении несуществующей папки");
    }

    @Test
    @DisplayName("Переименование пустой папки в корневой папке")
    void shouldRenameEmptyFolderInRoot() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "old-folder");

        folderService.rename(ROOT_USER_FOLDER, "old-folder", "new-folder");

        assertFalse(folderExists(ROOT_USER_FOLDER + "old-folder/"), "Старая папка должна исчезнуть");
        assertTrue(folderExists(ROOT_USER_FOLDER + "new-folder/"), "Новая папка должна появиться");
    }

    @Test
    @DisplayName("Переименование пустой вложенной папки")
    void shouldRenameEmptyNestedFolder() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "first-folder");
        folderService.create(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, "old-folder");

        folderService.rename(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, "old-folder", "new-folder");

        assertFalse(folderExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + "old-folder/"),
                "Старая папка должна исчезнуть");
        assertTrue(folderExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + "new-folder/"),
                "Новая папка должна появиться");
    }

    @Test
    @DisplayName("Попытка переименования несуществующей папки")
    void shouldThrowExceptionWhenRenamingNonExistentFolder() {
        assertThrows(FolderNotFoundException.class,
                () -> folderService.rename(ROOT_USER_FOLDER, "non-existent", "new-folder"),
                "Должно выбросить исключение при переименовании несуществующей папки");
    }

    @Test
    @DisplayName("Попытка переименования папки в уже существующую")
    void shouldThrowExceptionWhenRenamingToExistingFolder() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "old-folder");
        folderService.create(ROOT_USER_FOLDER, "new-folder");

        assertThrows(StorageException.class,
                () -> folderService.rename(ROOT_USER_FOLDER, "old-folder", "new-folder"),
                "Должно выбросить исключение при переименовании в существующую папку");
    }

    @Test
    @DisplayName("Переименование папки с вложенными объектами")
    void shouldRenameFolderWithNestedObjects() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "old-folder");
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + "old-folder/" + TEST_FILE_NAME)
                .stream(new ByteArrayInputStream(HELLO_MINIO_BYTES), HELLO_MINIO_BYTES.length, -1)
                .build());

        folderService.rename(ROOT_USER_FOLDER, "old-folder", "new-folder");

        assertFalse(folderExists(ROOT_USER_FOLDER + "old-folder/"), "Старая папка должна исчезнуть");
        assertTrue(folderExists(ROOT_USER_FOLDER + "new-folder/"), "Новая папка должна появиться");
        assertTrue(fileExists(ROOT_USER_FOLDER + "new-folder/" + TEST_FILE_NAME), "Вложенный файл должен сохраниться");
    }

    @Test
    @DisplayName("Загрузка папки в корневую папку пользователя")
    void shouldUploadFolderToRootFolder() throws Exception {
        MultipartFile file1 = new MockMultipartFile(
                "file", "uploaded-folder/file1.txt", "text/plain", HELLO_MINIO_BYTES);
        MultipartFile file2 = new MockMultipartFile(
                "file", "uploaded-folder/subfolder/file2.txt", "text/plain", HELLO_MINIO_BYTES);

        folderService.upload(ROOT_USER_FOLDER, "uploaded-folder", new MultipartFile[]{file1, file2});

        assertTrue(folderExists(ROOT_USER_FOLDER + "uploaded-folder/"),
                "Корневая папка загрузки должна быть создана");
        assertTrue(fileExists(ROOT_USER_FOLDER + "uploaded-folder/file1.txt"),
                "Файл на первом уровне должен быть загружен");
        assertTrue(folderExists(ROOT_USER_FOLDER + "uploaded-folder/subfolder/"),
                "Вложенная папка должна быть создана");
        assertTrue(fileExists(ROOT_USER_FOLDER + "uploaded-folder/subfolder/file2.txt"),
                "Файл во вложенной папке должен быть загружен");
    }

    @Test
    @DisplayName("Загрузка папки во вложенную папку")
    void shouldUploadFolderToNestedFolder() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "first-folder");
        MultipartFile file1 = new MockMultipartFile(
                "file", "uploaded-folder/file1.txt", "text/plain", HELLO_MINIO_BYTES);
        MultipartFile file2 = new MockMultipartFile(
                "file", "uploaded-folder/subfolder/file2.txt", "text/plain", HELLO_MINIO_BYTES);

        folderService.upload(
                ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER, "uploaded-folder", new MultipartFile[]{file1, file2});

        assertTrue(folderExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + "uploaded-folder/"),
                "Корневая папка загрузки должна быть создана");
        assertTrue(fileExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + "uploaded-folder/file1.txt"),
                "Файл на первом уровне должен быть загружен");
        assertTrue(folderExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + "uploaded-folder/subfolder/"),
                "Вложенная папка должна быть создана");
        assertTrue(fileExists(ROOT_USER_FOLDER + FIRST_LEVEL_FOLDER + "uploaded-folder/subfolder/file2.txt"),
                "Файл во вложенной папке должен быть загружен");
    }

    @Test
    @DisplayName("Попытка загрузки файлов в несуществующую папку")
    void shouldThrowExceptionWhenUploadingToNonExistentFolder() {
        assertThrows(FolderNotFoundException.class,
                () -> folderService.upload(
                        ROOT_USER_FOLDER + "non-existent/", "uploaded-folder", new MultipartFile[]{MULTIPART_TEST_FILE}),
                "Должно выбросить исключение при загрузке в несуществующую папку");
    }

    @Test
    @DisplayName("Попытка загрузки файлов с уже существующим именем папки")
    void shouldThrowExceptionWhenUploadingToExistingFolder() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "uploaded-folder");

        assertThrows(StorageException.class,
                () -> folderService.upload(
                        ROOT_USER_FOLDER, "uploaded-folder", new MultipartFile[]{MULTIPART_TEST_FILE}),
                "Должно выбросить исключение при загрузке в существующую папку");
    }

    @Test
    @DisplayName("Скачивание папки в виде ZIP-архива")
    void shouldDownloadFolderAsZip() throws Exception {
        folderService.create(ROOT_USER_FOLDER, "download-folder");
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(TEST_BUCKET_NAME)
                .object(ROOT_USER_FOLDER + "download-folder/" + TEST_FILE_NAME)
                .stream(new ByteArrayInputStream(HELLO_MINIO_BYTES), HELLO_MINIO_BYTES.length, -1)
                .build());

        InputStreamResource resource = folderService.download(ROOT_USER_FOLDER, "download-folder");

        assertNotNull(resource, "Ресурс ZIP-архива не должен быть null");
    }

    @Test
    @DisplayName("Попытка скачивания несуществующей папки")
    void shouldThrowExceptionWhenDownloadingNonExistentFolder() {
        assertThrows(FolderNotFoundException.class,
                () -> folderService.download(ROOT_USER_FOLDER, "non-existent"),
                "Должно выбросить исключение при скачивании несуществующей папки");
    }
}
