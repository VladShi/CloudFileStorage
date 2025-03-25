package ru.vladshi.cloudfilestorage.storage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.vladshi.cloudfilestorage.storage.model.FullItemPath;
import ru.vladshi.cloudfilestorage.security.annotation.FullPath;
import ru.vladshi.cloudfilestorage.storage.service.MinioService;

@Controller
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

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