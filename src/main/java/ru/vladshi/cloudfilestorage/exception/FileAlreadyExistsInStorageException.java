package ru.vladshi.cloudfilestorage.exception;

public class FileAlreadyExistsInStorageException extends RuntimeException {
    public FileAlreadyExistsInStorageException(String message) {
        super(message);
    }
}
