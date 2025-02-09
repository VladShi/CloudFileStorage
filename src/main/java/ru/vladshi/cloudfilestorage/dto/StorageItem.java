package ru.vladshi.cloudfilestorage.dto;

public record StorageItem(String relativePath, boolean isFolder, long size) {

    public String getName() {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Object relativePath cannot be null or empty");
        }

        String pathWithName = isFolder ? relativePath.substring(0, relativePath.length() - 1) : relativePath;

        int lastSlashIndex = pathWithName.lastIndexOf('/');

        return pathWithName.substring(lastSlashIndex + 1);
    }
}
