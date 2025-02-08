package ru.vladshi.cloudfilestorage.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.vladshi.cloudfilestorage.dto.StorageItem;
import ru.vladshi.cloudfilestorage.util.BreadcrumbUtil;

import java.util.ArrayList;
import java.util.List;

@Controller
public class FileStorageController {

    @GetMapping("/")
    public String showFiles(@AuthenticationPrincipal UserDetails userDetails,
                            Model model,
                            @RequestParam(required = false) String path) {
        model.addAttribute("breadcrumbs", BreadcrumbUtil.buildBreadcrumbs(path));

        // Пример данных для списка файлов и папок
        List<StorageItem> items = new ArrayList<>();
        items.add(new StorageItem("folder1/", true, 0));
        items.add(new StorageItem("folder2/", true, 0));
        items.add(new StorageItem("file1.txt", false, 1024));
        items.add(new StorageItem("file2.txt", false, 2048));
        model.addAttribute("items", items);

        return "file-storage";
    }
}
