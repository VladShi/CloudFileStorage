package ru.vladshi.cloudfilestorage.storage.exception;

public class FileAlreadyExistsInStorageException extends StorageException {
    public FileAlreadyExistsInStorageException(String fileName) {
        super("File already exists %s".formatted(fileName));
    }
}
