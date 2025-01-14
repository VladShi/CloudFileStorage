package ru.vladshi.cloudfilestorage.dto;

public record StorageItem(String name, boolean isFolder, long size) {
}
