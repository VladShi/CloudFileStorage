package ru.vladshi.cloudfilestorage.util;

import ru.vladshi.cloudfilestorage.model.Breadcrumb;

import java.util.ArrayList;
import java.util.List;

public final class BreadcrumbUtil {

    private BreadcrumbUtil() {
    }

    public static List<Breadcrumb> buildBreadcrumbs(String path) {

        List<Breadcrumb> breadcrumbs = new ArrayList<>();

        if (path != null && !path.isBlank()) {
            String[] parts = path.split("/");
            StringBuilder currentPath = new StringBuilder();

            for (String part : parts) {
                if (part.isBlank()) continue;
                currentPath.append(part).append("/");
                breadcrumbs.add(new Breadcrumb(currentPath.toString(), part));
            }
        }

        return breadcrumbs;
    }
}
