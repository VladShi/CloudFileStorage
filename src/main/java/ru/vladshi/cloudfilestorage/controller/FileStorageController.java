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
import ru.vladshi.cloudfilestorage.annotation.RedirectWithPath;
import ru.vladshi.cloudfilestorage.exception.*;
import ru.vladshi.cloudfilestorage.model.StorageItem;
import ru.vladshi.cloudfilestorage.model.FullItemPath;
import ru.vladshi.cloudfilestorage.model.UserStorageInfo;
import ru.vladshi.cloudfilestorage.security.annotation.FullPath;
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
    public String showFiles(@FullPath FullItemPath path,
                            Model model) throws Exception {

        List<StorageItem> storageItems = minioService.getItems(path.full());
        UserStorageInfo storageInfo = minioService.getUserStorageInfo(path.userPrefix());

        model.addAttribute("path", path.relativePath());
        model.addAttribute("breadcrumbs", BreadcrumbUtil.buildBreadcrumbs(path.relativePath()));
        model.addAttribute("items", storageItems);
        model.addAttribute("storageInfo", storageInfo);

        return "file-storage";
    }

    @PostMapping("/create-folder")
    @RedirectWithPath
    public void createFolder(@FullPath FullItemPath path,
                             @RequestParam String newFolderName,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            validateInputName(newFolderName);
            minioService.createFolder(path.full(), newFolderName.strip());
        } catch (FolderAlreadyExistsException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to create folder. " + e.getMessage());
        }
    }

    @PostMapping("/delete-folder")
    @RedirectWithPath
    public void deleteFolder(@FullPath FullItemPath path,
                             @RequestParam String folderToDelete,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.deleteFolder(path.full(), folderToDelete);
        } catch (ObjectDeletionException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to delete folder. " + e.getMessage());
        }
    }

    @PostMapping("/rename-folder")
    @RedirectWithPath
    public void renameFolder(@FullPath FullItemPath path,
                             @RequestParam String folderToRename,
                             @RequestParam String newFolderName,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            validateInputName(newFolderName);
            minioService.renameFolder(path.full(), folderToRename, newFolderName.strip());
        } catch (FolderAlreadyExistsException | ObjectDeletionException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to rename folder. " + e.getMessage());
        }
    }

    @PostMapping("/upload-file")
    @RedirectWithPath
    public void uploadFile(@FullPath FullItemPath path,
                           @RequestParam("file") MultipartFile file,
                           RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.checkStorageLimit(path.userPrefix(), file);
            minioService.uploadFile(path.full(), file);
        } catch (FileAlreadyExistsInStorageException | IllegalArgumentException | StorageLimitExceededException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to upload file. " + e.getMessage());
        }
    }

    @PostMapping("/delete-file")
    @RedirectWithPath
    public void deleteFile(@FullPath FullItemPath path,
                           @RequestParam String fileToDelete,
                           RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.deleteFile(path.full(), fileToDelete);
        } catch (FileNotFoundInStorageException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to delete file. " + e.getMessage());
        }
    }

    @PostMapping("/rename-file")
    @RedirectWithPath
    public void renameFile(@FullPath FullItemPath path,
                           @RequestParam String fileToRename,
                           @RequestParam String newFileName,
                           RedirectAttributes redirectAttributes) throws Exception {
        try {
            validateInputName(newFileName);
            minioService.renameFile(path.full(), fileToRename, newFileName.strip());
        } catch (FileAlreadyExistsInStorageException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to rename file. " + e.getMessage());
        }
    }

    @PostMapping("/upload-folder")
    @RedirectWithPath
    public void uploadFolder(@FullPath FullItemPath path,
                             @RequestParam String folderName,
                             @RequestParam("files") MultipartFile[] files,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.checkStorageLimit(path.userPrefix(), files);
            minioService.uploadFolder(path.full(), folderName, files);
        } catch (FolderAlreadyExistsException | IllegalArgumentException
                 | InputNameValidationException | StorageLimitExceededException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to upload folder. " + e.getMessage());
        }
    }

    @GetMapping("/download-file")
    public ResponseEntity<InputStreamResource> downloadFile(@FullPath FullItemPath path,
                                                            @RequestParam String fileName) throws Exception {

        InputStreamResource fileResource = minioService.downloadFile(path.full(), fileName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, createDispositionHeader(fileName));
        headers.add(HttpHeaders.CONTENT_TYPE, getContentType(fileName));
        long fileSize = minioService.getFileSize(path.full(), fileName);
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileResource);
    }

    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolder(@FullPath FullItemPath path,
                                                              @RequestParam String folderName) throws Exception {

        InputStreamResource folderResource = minioService.downloadFolder(path.full(), folderName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, createDispositionHeader(folderName) + ".zip");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(folderResource);
    }

    @GetMapping("/search")
    public String search(@FullPath FullItemPath path,
                         Model model,
                         @RequestParam(required = false) String searchQuery) throws Exception {
        model.addAttribute("items", minioService.searchItems(path.userPrefix(), searchQuery.strip()));
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
