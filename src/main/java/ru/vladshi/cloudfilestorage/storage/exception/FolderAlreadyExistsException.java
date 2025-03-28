package ru.vladshi.cloudfilestorage.storage.exception;

public class FolderAlreadyExistsException extends StorageException {
    public FolderAlreadyExistsException(String path) {
        super("Folder already exists: %s".formatted(path));
    }
}
