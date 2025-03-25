package ru.vladshi.cloudfilestorage.storage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vladshi.cloudfilestorage.security.annotation.FullPath;
import ru.vladshi.cloudfilestorage.storage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.storage.exception.ObjectDeletionException;
import ru.vladshi.cloudfilestorage.storage.exception.StorageItemNameValidationException;
import ru.vladshi.cloudfilestorage.storage.exception.StorageLimitExceededException;
import ru.vladshi.cloudfilestorage.storage.model.FullItemPath;
import ru.vladshi.cloudfilestorage.storage.service.FolderService;
import ru.vladshi.cloudfilestorage.storage.service.StorageUsageService;
import ru.vladshi.cloudfilestorage.storage.util.DispositionHeaderUtil;

import static ru.vladshi.cloudfilestorage.storage.util.RedirectUtil.redirectWithPath;

@Controller
@RequestMapping("folder")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final StorageUsageService storageUsageService;

    @PostMapping("/create")
    public String createFolder(@FullPath FullItemPath path,
                             @RequestParam String newFolderName,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            folderService.create(path.full(), newFolderName.strip());
        } catch (FolderAlreadyExistsException | StorageItemNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to create folder. " + e.getMessage());
        }
        return redirectWithPath(path.relative());
    }

    @PostMapping("/delete")
    public String deleteFolder(@FullPath FullItemPath path,
                             @RequestParam String folderToDelete,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            folderService.delete(path.full(), folderToDelete);
        } catch (ObjectDeletionException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to delete folder. " + e.getMessage());
        }
        return redirectWithPath(path.relative());
    }

    @PostMapping("/rename")
    public String renameFolder(@FullPath FullItemPath path,
                             @RequestParam String folderToRename,
                             @RequestParam String newFolderName,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            folderService.rename(path.full(), folderToRename, newFolderName.strip());
        } catch (FolderAlreadyExistsException | ObjectDeletionException | StorageItemNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to rename folder. " + e.getMessage());
        }
        return redirectWithPath(path.relative());
    }

    @PostMapping("/upload")
    public String uploadFolder(@FullPath FullItemPath path,
                             @RequestParam String folderName,
                             @RequestParam("files") MultipartFile[] files,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            storageUsageService.checkLimit(path.userPrefix(), files);
            folderService.upload(path.full(), folderName, files);
        } catch (FolderAlreadyExistsException | IllegalArgumentException
                 | StorageItemNameValidationException | StorageLimitExceededException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to upload folder. " + e.getMessage());
        }
        return redirectWithPath(path.relative());
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFolder(@FullPath FullItemPath path,
                                                              @RequestParam String folderName) throws Exception {

        InputStreamResource folderResource = folderService.download(path.full(), folderName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, DispositionHeaderUtil.build(folderName) + ".zip");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(folderResource);
    }
}