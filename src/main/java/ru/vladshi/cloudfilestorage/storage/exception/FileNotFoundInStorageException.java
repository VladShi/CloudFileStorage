package ru.vladshi.cloudfilestorage.storage.exception;

public class FileNotFoundInStorageException extends StorageException {
    public FileNotFoundInStorageException(String fileName) {
        super("File %s not found.".formatted(fileName));
    }
}
