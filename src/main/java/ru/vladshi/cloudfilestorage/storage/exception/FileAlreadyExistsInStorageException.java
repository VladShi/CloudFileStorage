package ru.vladshi.cloudfilestorage.storage.exception;

public class FileAlreadyExistsInStorageException extends RuntimeException {
    public FileAlreadyExistsInStorageException(String message) {
        super(message);
    }
}
