package ru.vladshi.cloudfilestorage.exception;

public class FileNotFoundInStorageException extends RuntimeException {
    public FileNotFoundInStorageException(String message) {
        super(message);
    }
}
