package ru.vladshi.cloudfilestorage.storage.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class DispositionHeaderUtil {

    private DispositionHeaderUtil() {
    }

    public static String build(String name) {
        String encodedFileName = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encodedFileName;
    }
}
