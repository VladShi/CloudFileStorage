package ru.vladshi.cloudfilestorage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vladshi.cloudfilestorage.exception.*;
import ru.vladshi.cloudfilestorage.model.StorageItem;
import ru.vladshi.cloudfilestorage.model.UserStorageInfo;
import ru.vladshi.cloudfilestorage.security.model.CustomUserDetails;
import ru.vladshi.cloudfilestorage.service.MinioService;
import ru.vladshi.cloudfilestorage.util.BreadcrumbUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class FileStorageController {

    private final MinioService minioService;

    @GetMapping("/")
    public String showFiles(@AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model,
                            @RequestParam(required = false) String path) throws Exception {

        List<StorageItem> storageItems = minioService.getItems(userDetails.getUsername(), path);
        UserStorageInfo storageInfo = minioService.getUserStorageInfo(userDetails.getUsername());

        model.addAttribute("path", path);
        model.addAttribute("breadcrumbs", BreadcrumbUtil.buildBreadcrumbs(path));
        model.addAttribute("items", storageItems);
        model.addAttribute("storageInfo", storageInfo);

        return "file-storage";
    }

    @PostMapping("/create-folder")
        public String createFolder(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @RequestParam(required = false) String path,
                               @RequestParam String newFolderName,
                               RedirectAttributes redirectAttributes) throws Exception {
        try {
            validateInputName(newFolderName);
            minioService.createFolder(userDetails.getUsername(), path, newFolderName.strip());
        } catch (FolderAlreadyExistsException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to create folder. " + e.getMessage());
        }
        return redirectByPath(path);
    }

    @PostMapping("/delete-folder")
    public String deleteFolder(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @RequestParam(required = false) String path,
                               @RequestParam String folderToDelete,
                               RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.deleteFolder(userDetails.getUsername(), path, folderToDelete);
        } catch (ObjectDeletionException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to delete folder. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @PostMapping("/rename-folder")
    public String renameFolder(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @RequestParam(required = false) String path,
                               @RequestParam String folderToRename,
                               @RequestParam String newFolderName,
                               RedirectAttributes redirectAttributes) throws Exception {
        try {
            validateInputName(newFolderName);
            minioService.renameFolder(userDetails.getUsername(), path, folderToRename, newFolderName.strip());
        } catch (FolderAlreadyExistsException | ObjectDeletionException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to rename folder. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @PostMapping("/upload-file")
    public String uploadFile(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @RequestParam(required = false) String path,
                               @RequestParam("file") MultipartFile file,
                               RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.uploadFile(userDetails.getUsername(), path, file);
        } catch (FileAlreadyExistsInStorageException | IllegalArgumentException | StorageLimitExceededException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to upload file. " + e.getMessage());
        }

        return redirectByPath(path); // TODO увеличить максимальный размер загружаемого файла в спринге (по-умолчанию 30мб)
    }

    @PostMapping("/delete-file")
    public String deleteFile(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @RequestParam(required = false) String path,
                               @RequestParam String fileToDelete) throws Exception {

        minioService.deleteFile(userDetails.getUsername(), path, fileToDelete);

        return redirectByPath(path);
    }

    @PostMapping("/rename-file")
    public String renameFile(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @RequestParam(required = false) String path,
                               @RequestParam String fileToRename,
                               @RequestParam String newFileName,
                               RedirectAttributes redirectAttributes) throws Exception {
        try {
            validateInputName(newFileName);
            minioService.renameFile(userDetails.getUsername(), path, fileToRename, newFileName.strip());
        } catch (FileAlreadyExistsInStorageException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to rename file. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @PostMapping("/upload-folder")
    public String uploadFolder(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @RequestParam(required = false) String path,
                             @RequestParam String folderName,
                             @RequestParam("files") MultipartFile[] files,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.uploadFolder(userDetails.getUsername(), path, folderName, files);
        } catch (FolderAlreadyExistsException | IllegalArgumentException
                 | InputNameValidationException | StorageLimitExceededException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to upload folder. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @GetMapping("/download-file")
    public ResponseEntity<InputStreamResource> downloadFile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                            @RequestParam(required = false) String path,
                                                            @RequestParam String fileName) throws Exception {

        InputStreamResource fileResource = minioService.downloadFile(userDetails.getUsername(), path, fileName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, createDispositionHeader(fileName));
        headers.add(HttpHeaders.CONTENT_TYPE, getContentType(fileName));
        long fileSize = minioService.getFileSize(userDetails.getUsername(), path, fileName);
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileResource);
    }

    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolder(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                            @RequestParam(required = false) String path,
                                                            @RequestParam String folderName) throws Exception {

        InputStreamResource folderResource = minioService.downloadFolder(userDetails.getUsername(), path, folderName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, createDispositionHeader(folderName) + ".zip");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(folderResource);
    }

    @GetMapping("/search")
    public String search(@AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model,
                         @RequestParam(required = false) String searchQuery) throws Exception {
        model.addAttribute("items", minioService.searchItems(userDetails.getUsername(), searchQuery.strip()));
        model.addAttribute("searchQuery", searchQuery);

        return "search";
    }

    private String createDispositionHeader(String name) {
        String encodedFileName = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encodedFileName;
    }

    private String getContentType(String fileName) {
        try {
            String mimeType = Files.probeContentType(Path.of(fileName));
            return mimeType != null ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } catch (IOException e) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    private static String redirectByPath(String path) {
        if (path != null && !path.isBlank()) {
            return "redirect:/?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
        }
        return "redirect:/";
    }

    private void validateInputName(String name) {
        if (name == null || name.isBlank()) {
            throw new InputNameValidationException("Name cannot be empty");
        }
        if (name.length() > 60) {
            throw new InputNameValidationException("Name cannot be longer than 60 characters");
        }
        String forbiddenChars = "/<>:\"?\\|*\0";
        for (char ch : forbiddenChars.toCharArray()) {
            if (name.indexOf(ch) != -1) {
                throw new InputNameValidationException(
                        "Name contains invalid character: '" + ch + "' . Forbidden characters: " + forbiddenChars);
            }
        }
        if (name.endsWith(".")) {
            throw new InputNameValidationException("Name cannot end with a dot");
        }
    }
}
