package ru.vladshi.cloudfilestorage.storage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.vladshi.cloudfilestorage.security.annotation.FullPath;
import ru.vladshi.cloudfilestorage.storage.model.FullItemPath;
import ru.vladshi.cloudfilestorage.storage.model.StorageItem;
import ru.vladshi.cloudfilestorage.storage.model.StorageUsageInfo;
import ru.vladshi.cloudfilestorage.storage.service.FolderService;
import ru.vladshi.cloudfilestorage.storage.service.StorageUsageService;
import ru.vladshi.cloudfilestorage.storage.util.BreadcrumbUtil;

import java.util.List;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class StorageMainController {

    private final FolderService folderService;
    private final StorageUsageService storageUsageService;

    @GetMapping
    public String showFiles(@FullPath FullItemPath path, Model model) throws Exception {

        List<StorageItem> storageItems = folderService.getFolderContents(path.full());
        StorageUsageInfo storageInfo = storageUsageService.getInfo(path.userPrefix());

        model.addAttribute("path", path.relative());
        model.addAttribute("breadcrumbs", BreadcrumbUtil.buildBreadcrumbs(path.relative()));
        model.addAttribute("items", storageItems);
        model.addAttribute("storageInfo", storageInfo);

        return "file-storage";
    }
}