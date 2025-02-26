package ru.vladshi.cloudfilestorage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vladshi.cloudfilestorage.dto.StorageItem;
import ru.vladshi.cloudfilestorage.exception.FileAlreadyExistsInStorageException;
import ru.vladshi.cloudfilestorage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.exception.InputNameValidationException;
import ru.vladshi.cloudfilestorage.exception.ObjectDeletionException;
import ru.vladshi.cloudfilestorage.service.MinioService;
import ru.vladshi.cloudfilestorage.util.BreadcrumbUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class FileStorageController {

    private final MinioService minioService;

    @GetMapping("/")
    public String showFiles(@AuthenticationPrincipal UserDetails userDetails,
                            Model model,
                            @RequestParam(required = false) String path) throws Exception {

        List<StorageItem> storageItems = minioService.getItems(userDetails.getUsername(), path);

        model.addAttribute("path", path);
        model.addAttribute("breadcrumbs", BreadcrumbUtil.buildBreadcrumbs(path));
        model.addAttribute("items", storageItems);

        return "file-storage";
    }

    @PostMapping("/create-folder")
    public String createFolder(@AuthenticationPrincipal UserDetails userDetails,
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
    public String deleteFolder(@AuthenticationPrincipal UserDetails userDetails,
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
    public String renameFolder(@AuthenticationPrincipal UserDetails userDetails,
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
    public String uploadFile(@AuthenticationPrincipal UserDetails userDetails,
                               @RequestParam(required = false) String path,
                               @RequestParam("file") MultipartFile file,
                               RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.uploadFile(userDetails.getUsername(), path, file);
        } catch (FileAlreadyExistsInStorageException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to upload file. " + e.getMessage());
        }

        return redirectByPath(path); // TODO увеличить максимальный размер загружаемого файла в спринге (по-умолчанию 30мб)
    }

    @PostMapping("/delete-file")
    public String deleteFile(@AuthenticationPrincipal UserDetails userDetails,
                               @RequestParam(required = false) String path,
                               @RequestParam String fileToDelete) throws Exception {

        minioService.deleteFile(userDetails.getUsername(), path, fileToDelete);

        return redirectByPath(path);
    }

    @PostMapping("/rename-file")
    public String renameFile(@AuthenticationPrincipal UserDetails userDetails,
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
    public String uploadFolder(@AuthenticationPrincipal UserDetails userDetails,
                             @RequestParam(required = false) String path,
                             @RequestParam String folderName,
                             @RequestParam("files") MultipartFile[] files,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            minioService.uploadFolder(userDetails.getUsername(), path, folderName, files);
        } catch (FolderAlreadyExistsException | IllegalArgumentException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to upload folder. " + e.getMessage());
        }

        return redirectByPath(path);
    }

    @GetMapping("/download-file")
    public ResponseEntity<InputStreamResource> downloadFile(@AuthenticationPrincipal UserDetails userDetails,
                                                            @RequestParam(required = false) String path,
                                                            @RequestParam String fileName) throws Exception {

        InputStreamResource fileResource = minioService.downloadFile(userDetails.getUsername(), path, fileName);

        HttpHeaders headers = new HttpHeaders();
        String encodedFileName = encodeNameForContentDisposition(fileName);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encodedFileName);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileResource);
    }

    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolder(@AuthenticationPrincipal UserDetails userDetails,
                                                            @RequestParam(required = false) String path,
                                                            @RequestParam String folderName) throws Exception {

        InputStreamResource folderResource = minioService.downloadFolder(userDetails.getUsername(), path, folderName);

        HttpHeaders headers = new HttpHeaders();
        String encodedFolderName = encodeNameForContentDisposition(folderName);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encodedFolderName + ".zip");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(folderResource);
    }

    @GetMapping("/search")
    public String search(@AuthenticationPrincipal UserDetails userDetails,
                         Model model,
                         @RequestParam(required = false) String searchQuery) throws Exception {
        model.addAttribute("items", minioService.searchItems(userDetails.getUsername(), searchQuery.strip()));
        model.addAttribute("searchQuery", searchQuery);

        return "search";
    }

    private String encodeNameForContentDisposition(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
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
