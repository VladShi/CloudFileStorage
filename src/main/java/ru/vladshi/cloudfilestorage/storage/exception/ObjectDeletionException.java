package ru.vladshi.cloudfilestorage.storage.exception;

public class ObjectDeletionException extends StorageException {
    public ObjectDeletionException(String path) {
        super("Failed to delete object: %s".formatted(path));
    }
}
