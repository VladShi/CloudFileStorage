package ru.vladshi.cloudfilestorage.controller;

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
import ru.vladshi.cloudfilestorage.annotation.RedirectWithPath;
import ru.vladshi.cloudfilestorage.exception.FolderAlreadyExistsException;
import ru.vladshi.cloudfilestorage.exception.InputNameValidationException;
import ru.vladshi.cloudfilestorage.exception.ObjectDeletionException;
import ru.vladshi.cloudfilestorage.exception.StorageLimitExceededException;
import ru.vladshi.cloudfilestorage.model.FullItemPath;
import ru.vladshi.cloudfilestorage.security.annotation.FullPath;
import ru.vladshi.cloudfilestorage.service.MinioService;
import ru.vladshi.cloudfilestorage.util.DispositionHeaderUtil;
import ru.vladshi.cloudfilestorage.validation.StorageItemNameValidator;

@Controller
@RequestMapping("folder")
@RequiredArgsConstructor
public class StorageFolderController {

    private final MinioService minioService;

    @PostMapping("/create")
    @RedirectWithPath
    public void createFolder(@FullPath FullItemPath path,
                             @RequestParam String newFolderName,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            StorageItemNameValidator.validate(newFolderName);
            minioService.createFolder(path.full(), newFolderName.strip());
        } catch (FolderAlreadyExistsException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to create folder. " + e.getMessage());
        }
    }

    @PostMapping("/delete")
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

    @PostMapping("/rename")
    @RedirectWithPath
    public void renameFolder(@FullPath FullItemPath path,
                             @RequestParam String folderToRename,
                             @RequestParam String newFolderName,
                             RedirectAttributes redirectAttributes) throws Exception {
        try {
            StorageItemNameValidator.validate(newFolderName);
            minioService.renameFolder(path.full(), folderToRename, newFolderName.strip());
        } catch (FolderAlreadyExistsException | ObjectDeletionException | InputNameValidationException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Failed to rename folder. " + e.getMessage());
        }
    }

    @PostMapping("/upload")
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

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFolder(@FullPath FullItemPath path,
                                                              @RequestParam String folderName) throws Exception {

        InputStreamResource folderResource = minioService.downloadFolder(path.full(), folderName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, DispositionHeaderUtil.build(folderName) + ".zip");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(folderResource);
    }
}