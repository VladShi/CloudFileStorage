package ru.vladshi.cloudfilestorage.storage.model;

public record FullItemPath(String userPrefix, String relative) {

    public String full() {
        if (relative == null || relative.isBlank()) {
            return userPrefix;
        }
        String fullPrefix = userPrefix + relative;
        if (!fullPrefix.endsWith("/")) {
            fullPrefix += "/";
        }
        return fullPrefix;
    }
}
