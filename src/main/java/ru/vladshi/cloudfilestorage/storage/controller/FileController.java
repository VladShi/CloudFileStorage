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
import ru.vladshi.cloudfilestorage.storage.service.FileService;
import ru.vladshi.cloudfilestorage.storage.service.StorageUsageService;
import ru.vladshi.cloudfilestorage.storage.util.DispositionHeaderUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static ru.vladshi.cloudfilestorage.storage.util.RedirectUtil.redirectWithPath;

@Controller
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final StorageUsageService storageUsageService;

    @PostMapping("/delete")
    public String deleteFile(@FullPath FullItemPath path, @RequestParam String fileToDelete) throws Exception {
        fileService.delete(path.full(), fileToDelete);
        return redirectWithPath(path.relative());
    }

    @PostMapping("/rename")
    public String renameFile(@FullPath FullItemPath path,
                             @RequestParam String fileToRename,
                             @RequestParam String newFileName) throws Exception {
        fileService.rename(path.full(), fileToRename, newFileName.strip());
        return redirectWithPath(path.relative());
    }

    @PostMapping("/upload")
    public String uploadFile(@FullPath FullItemPath path, @RequestParam("file") MultipartFile file) throws Exception {
        storageUsageService.checkLimit(path.userPrefix(), file);
        fileService.upload(path.full(), file);
        return redirectWithPath(path.relative());
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadFile(@FullPath FullItemPath path,
                                                            @RequestParam String fileName) throws Exception {

        InputStreamResource fileResource = fileService.download(path.full(), fileName);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, DispositionHeaderUtil.build(fileName));
        headers.add(HttpHeaders.CONTENT_TYPE, getContentType(fileName));
        long fileSize = fileService.getFileSize(path.full(), fileName);
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileResource);
    }

    private String getContentType(String fileName) {
        try {
            String mimeType = Files.probeContentType(Path.of(fileName));
            return mimeType != null ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } catch (IOException e) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}