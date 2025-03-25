package ru.vladshi.cloudfilestorage.storage.model;

import ru.vladshi.cloudfilestorage.storage.util.SizeFormatter;

public record StorageItem(String relativePath, boolean isFolder, long size) {

    public String getName() {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Object relativePath cannot be null or empty");
        }

        String pathWithName = isFolder ? relativePath.substring(0, relativePath.length() - 1) : relativePath;

        int lastSlashIndex = pathWithName.lastIndexOf('/');

        return pathWithName.substring(lastSlashIndex + 1);
    }

    public String getParentPath() {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Object relativePath cannot be null or empty");
        }

        String pathWithName = isFolder ? relativePath.substring(0, relativePath.length() - 1) : relativePath;

        int lastSlashIndex = pathWithName.lastIndexOf('/');

        return relativePath.substring(0, lastSlashIndex + 1);
    }

    public String getFormattedSize() {
        if (isFolder) {
            return "-";
        }
        return SizeFormatter.formatSize(size);
    }
}
