package ru.vladshi.cloudfilestorage.util;

public final class SizeFormatter {

    private SizeFormatter() {
    }

    public static String formatSize(long sizeInBytes) {
        if (sizeInBytes < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }

        if (sizeInBytes >= 1024 * 1024 * 1024) {
            return String.format("%.1f GB", sizeInBytes / (1024.0 * 1024 * 1024));
        } else if (sizeInBytes >= 1024 * 1024) {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024));
        } else if (sizeInBytes >= 1024) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else {
            return sizeInBytes + " B";
        }
    }
}
