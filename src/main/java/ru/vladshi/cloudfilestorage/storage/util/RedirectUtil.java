package ru.vladshi.cloudfilestorage.storage.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class RedirectUtil {
    private RedirectUtil() {}

    public static String redirectWithPath(String redirectMapping, String pathValue) {
        String encodedPath = (pathValue != null && !pathValue.isBlank())
                ? "?path=" + URLEncoder.encode(pathValue, StandardCharsets.UTF_8)
                : "";
        return "redirect:" + redirectMapping + encodedPath;
    }

    public static String redirectWithPath(String pathValue) {
        return redirectWithPath("/", pathValue);
    }
}
