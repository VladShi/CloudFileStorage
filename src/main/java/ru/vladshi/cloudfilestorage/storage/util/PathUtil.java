package ru.vladshi.cloudfilestorage.storage.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathUtil {
    private PathUtil() {}

    public static String extractNameFromPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        Path path = Paths.get(fullPath);
        return path.getFileName() != null ? path.getFileName().toString() : "";
    }

    public static String removeRootFolder(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        int firstSlash = path.indexOf('/');
        if (firstSlash == -1) {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
        return path.substring(firstSlash + 1);
    }
}
