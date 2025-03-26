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
import ru.vladshi.cloudfilestorage.security.annotation.FullPath;
import ru.vladshi.cloudfilestorage.storage.model.FullItemPath;
import ru.vladshi.cloudfilestorage.storage.service.FolderService;
import ru.vladshi.cloudfilestorage.storage.service.StorageUsageService;
import ru.vladshi.cloudfilestorage.storage.util.HttpHeaderUtil;

import static ru.vladshi.cloudfilestorage.storage.util.RedirectUtil.redirectWithPath;

@Controller
@RequestMapping("folder")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final StorageUsageService storageUsageService;

    @PostMapping("/create")
    public String createFolder(@FullPath FullItemPath path, @RequestParam String newFolderName) throws Exception {
        folderService.create(path.full(), newFolderName.strip());
        return redirectWithPath(path.relative());
    }

    @PostMapping("/delete")
    public String deleteFolder(@FullPath FullItemPath path, @RequestParam String folderToDelete) throws Exception {
        folderService.delete(path.full(), folderToDelete);
        return redirectWithPath(path.relative());
    }

    @PostMapping("/rename")
    public String renameFolder(@FullPath FullItemPath path,
                               @RequestParam String folderToRename,
                               @RequestParam String newFolderName) throws Exception {
        folderService.rename(path.full(), folderToRename, newFolderName.strip());
        return redirectWithPath(path.relative());
    }

    @PostMapping("/upload")
    public String uploadFolder(@FullPath FullItemPath path,
                               @RequestParam String folderName,
                               @RequestParam("files") MultipartFile[] files) throws Exception {
        storageUsageService.checkLimit(path.userPrefix(), files);
        folderService.upload(path.full(), folderName, files);
        return redirectWithPath(path.relative());
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFolder(@FullPath FullItemPath path,
                                                              @RequestParam String folderName) throws Exception {

        InputStreamResource folderResource = folderService.download(path.full(), folderName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, HttpHeaderUtil.buildContentDisposition(folderName) + ".zip");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(folderResource);
    }
}