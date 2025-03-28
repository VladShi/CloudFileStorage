package ru.vladshi.cloudfilestorage.storage.validation;

import ru.vladshi.cloudfilestorage.storage.exception.StorageItemNameValidationException;

public final class StorageItemNameValidator {

    private static final String FORBIDDEN_CHARS = "/<>:\"?\\|*\0";
    private static final int MAX_LENGTH = 60;

    private StorageItemNameValidator() {
    }

    public static void validate(String name) throws StorageItemNameValidationException {
        if (name == null || name.isBlank()) {
            throw new StorageItemNameValidationException("Name cannot be empty");
        }
        if (name.length() > MAX_LENGTH) {
            throw new StorageItemNameValidationException("Name cannot be longer than " + MAX_LENGTH + " characters");
        }
        for (char ch : FORBIDDEN_CHARS.toCharArray()) {
            if (name.indexOf(ch) != -1) {
                throw new StorageItemNameValidationException(
                        "Name contains invalid character: '" + ch + "'. Forbidden characters: " + FORBIDDEN_CHARS);
            }
        }
        if (name.endsWith(".")) {
            throw new StorageItemNameValidationException("Name cannot end with a dot");
        }
    }
}
