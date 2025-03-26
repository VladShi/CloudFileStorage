package ru.vladshi.cloudfilestorage.storage.exception;

public class FolderNotFoundException extends RuntimeException {
    public FolderNotFoundException(String path) {
        super("Folder does not exist: %s".formatted(path));
    }
}
