package ru.vladshi.cloudfilestorage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import ru.vladshi.cloudfilestorage.security.annotation.UserPrefix;
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
    public String showFiles(@UserPrefix String userPrefix,
                            Model model,
                            @RequestParam(required = false) String path) throws Exception {

        List<StorageItem> storageItems = minioService.getItems(userPrefix, path);
        UserStorageInfo storageInfo = minioService.getUserStorageInfo(userPrefix);

        model.addAttribute("path", path);
        model.addAttribute("breadcrumbs", BreadcrumbUtil.buildBreadcrumbs(path));
        model.addAttribute("items", storageItems);
        model.addAttribute("storageInfo", storageInfo);

        return "file-storage";
    }

    @PostMapping("/create-folder")
        public String createFolder(@UserPrefix String userPrefix,
                                   @RequestParam(required = false) String path,
                                   @RequestParam String newFolderName,
                                   RedirectAttributes redirectAttributes) throws Exception {
        try {
            validateInputName(newFolderName);
            minioService.createFolder(userPrefix, path, newFolderName.strip());
        } catch (FolderAlreadyExistsException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to create folder. " + e.getMessage());
        }
        return redirectByPath(path);
    }

    @PostMapping("/delete-folder")
    public String deleteFolder(@UserPrefix String userPrefix,
                               @RequestParam(required = false) String path,
                               @RequestParam String folderToDelete,
                               RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.deleteFolder(userPrefix, path, folderToDelete);
        } catch (ObjectDeletionException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to delete folder. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @PostMapping("/rename-folder")
    public String renameFolder(@UserPrefix String userPrefix,
                               @RequestParam(required = false) String path,
                               @RequestParam String folderToRename,
                               @RequestParam String newFolderName,
                               RedirectAttributes redirectAttributes) throws Exception {
        try {
            validateInputName(newFolderName);
            minioService.renameFolder(userPrefix, path, folderToRename, newFolderName.strip());
        } catch (FolderAlreadyExistsException | ObjectDeletionException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to rename folder. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @PostMapping("/upload-file")
    public String uploadFile(@UserPrefix String userPrefix,
                             @RequestParam(required = false) String path,
                             @RequestParam("file") MultipartFile file,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.uploadFile(userPrefix, path, file);
        } catch (FileAlreadyExistsInStorageException | IllegalArgumentException | StorageLimitExceededException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to upload file. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @PostMapping("/delete-file")
    public String deleteFile(@UserPrefix String userPrefix,
                             @RequestParam(required = false) String path,
                             @RequestParam String fileToDelete) throws Exception {

        minioService.deleteFile(userPrefix, path, fileToDelete);

        return redirectByPath(path);
    }

    @PostMapping("/rename-file")
    public String renameFile(@UserPrefix String userPrefix,
                             @RequestParam(required = false) String path,
                             @RequestParam String fileToRename,
                             @RequestParam String newFileName,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            validateInputName(newFileName);
            minioService.renameFile(userPrefix, path, fileToRename, newFileName.strip());
        } catch (FileAlreadyExistsInStorageException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to rename file. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @PostMapping("/upload-folder")
    public String uploadFolder(@UserPrefix String userPrefix,
                               @RequestParam(required = false) String path,
                               @RequestParam String folderName,
                               @RequestParam("files") MultipartFile[] files,
                               RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.uploadFolder(userPrefix, path, folderName, files);
        } catch (FolderAlreadyExistsException | IllegalArgumentException
                 | InputNameValidationException | StorageLimitExceededException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to upload folder. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @GetMapping("/download-file")
    public ResponseEntity<InputStreamResource> downloadFile(@UserPrefix String userPrefix,
                                                            @RequestParam(required = false) String path,
                                                            @RequestParam String fileName) throws Exception {

        InputStreamResource fileResource = minioService.downloadFile(userPrefix, path, fileName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, createDispositionHeader(fileName));
        headers.add(HttpHeaders.CONTENT_TYPE, getContentType(fileName));
        long fileSize = minioService.getFileSize(userPrefix, path, fileName);
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileResource);
    }

    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolder(@UserPrefix String userPrefix,
                                                              @RequestParam(required = false) String path,
                                                              @RequestParam String folderName) throws Exception {

        InputStreamResource folderResource = minioService.downloadFolder(userPrefix, path, folderName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, createDispositionHeader(folderName) + ".zip");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(folderResource);
    }

    @GetMapping("/search")
    public String search(@UserPrefix String userPrefix,
                         Model model,
                         @RequestParam(required = false) String searchQuery) throws Exception {
        model.addAttribute("items", minioService.searchItems(userPrefix, searchQuery.strip()));
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
