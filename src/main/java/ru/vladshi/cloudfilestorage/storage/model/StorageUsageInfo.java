package ru.vladshi.cloudfilestorage.storage.model;

import ru.vladshi.cloudfilestorage.storage.util.SizeFormatter;

public record StorageUsageInfo(long currentSize, long maxSize) {

    public String getFormattedCurrentSize() {
        return SizeFormatter.formatSize(currentSize);
    }

    public String getFormattedMaxSize() {
        return SizeFormatter.formatSize(maxSize);
    }

    public String getUsageClass() {
        double percentage = (double) currentSize / maxSize * 100;
        if (percentage >= 90) return "error";
        if (percentage >= 70) return "warn";
        return "";
    }
}
