package ru.vladshi.cloudfilestorage.util;

public final class UserPrefixUtil {

    private UserPrefixUtil() {
    }

    public static String generateUserPrefix(String username, Long userId) {
        String normalizedUsername = normalizeUsername(username);
        return normalizedUsername + "-" + userId + "/";
    }

    private static String normalizeUsername(String username) {
        return username.toLowerCase()
                .replaceAll("[*$.]", "-")
                .replaceAll("-+", "-");
    }
}
