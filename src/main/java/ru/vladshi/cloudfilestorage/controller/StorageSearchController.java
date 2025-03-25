package ru.vladshi.cloudfilestorage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.vladshi.cloudfilestorage.model.FullItemPath;
import ru.vladshi.cloudfilestorage.security.annotation.FullPath;
import ru.vladshi.cloudfilestorage.service.MinioService;

@Controller
@RequestMapping("/search")
@RequiredArgsConstructor
public class StorageSearchController {

    private final MinioService minioService;

    @GetMapping
    public String search(@FullPath FullItemPath path,
                         Model model,
                         @RequestParam(required = false) String searchQuery) throws Exception {
        model.addAttribute("items", minioService.searchItems(path.userPrefix(), searchQuery.strip()));
        model.addAttribute("searchQuery", searchQuery);

        return "search";
    }
}