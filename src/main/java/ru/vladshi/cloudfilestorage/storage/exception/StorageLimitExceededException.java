package ru.vladshi.cloudfilestorage.storage.exception;

public class StorageLimitExceededException extends StorageException {
    public StorageLimitExceededException(String message) {
        super(message);
    }
}
