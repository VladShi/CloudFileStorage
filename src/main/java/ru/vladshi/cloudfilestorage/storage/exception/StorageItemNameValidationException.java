package ru.vladshi.cloudfilestorage.storage.exception;

public class StorageItemNameValidationException extends RuntimeException {
    public StorageItemNameValidationException(String message) {
        super(message);
    }
}
