package ru.vladshi.cloudfilestorage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vladshi.cloudfilestorage.service.MinioService;
import ru.vladshi.cloudfilestorage.util.BreadcrumbUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class FileStorageController {

    private final MinioService minioService;

    @GetMapping("/")
    public String showFiles(@AuthenticationPrincipal UserDetails userDetails,
                            Model model,
                            @RequestParam(required = false) String path) {

        model.addAttribute("path", path);
        model.addAttribute("breadcrumbs", BreadcrumbUtil.buildBreadcrumbs(path));
        model.addAttribute("items", minioService.getItems(userDetails.getUsername(), path));

        return "file-storage";
    }

    @PostMapping("/create-folder")
    public String createFolder(@AuthenticationPrincipal UserDetails userDetails,
                               @RequestParam(required = false) String path,
                               @RequestParam String newFolderName,
                               RedirectAttributes redirectAttributes) {
        try {
            minioService.createFolder(userDetails.getUsername(), path, newFolderName);
            redirectAttributes.addFlashAttribute("successMessage", "Folder created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to create folder. " + e.getMessage());
        }

        return "redirect:/?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
    }
}
