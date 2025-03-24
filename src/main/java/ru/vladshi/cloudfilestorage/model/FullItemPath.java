package ru.vladshi.cloudfilestorage.model;

public record FullItemPath(String userPrefix, String relativePath) {

    public String full() {
        if (relativePath == null || relativePath.isBlank()) {
            return userPrefix;
        }
        String fullPrefix = userPrefix + relativePath;
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }
        return fullPrefix;
    }
}
