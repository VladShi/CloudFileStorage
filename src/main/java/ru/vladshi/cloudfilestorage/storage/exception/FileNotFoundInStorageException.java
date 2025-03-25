package ru.vladshi.cloudfilestorage.storage.exception;

public class FileNotFoundInStorageException extends RuntimeException {
    public FileNotFoundInStorageException(String message) {
        super(message);
    }
}
