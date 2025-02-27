package ru.vladshi.cloudfilestorage.model;

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

        if (size < 1024) {
            return size + " B"; // Байты
        }

        double sizeInKB = size / 1024.0;
        if (sizeInKB < 1024) {
            return String.format("%.1f KB", sizeInKB);
        }

        double sizeInMB = sizeInKB / 1024.0;
        if (sizeInMB < 1024) {
            return String.format("%.1f MB", sizeInMB);
        }

        double sizeInGB = sizeInMB / 1024.0;
        return String.format("%.1f GB", sizeInGB);
    }
}
