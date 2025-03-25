package ru.vladshi.cloudfilestorage.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.vladshi.cloudfilestorage.storage.exception.FileNotFoundInStorageException;
import ru.vladshi.cloudfilestorage.storage.exception.FolderNotFoundException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(FileNotFoundInStorageException.class)
    public String handleFileNotFound(FileNotFoundInStorageException e, RedirectAttributes redirectAttributes) {
        log.info("File not found. {}", e.getMessage());
        redirectAttributes.addFlashAttribute("errorCode", HttpStatus.NOT_FOUND.value());
        redirectAttributes.addFlashAttribute("errorMessage", "File not found. " + e.getMessage());
        return "redirect:/error";
    }

    @ExceptionHandler(FolderNotFoundException.class)
    public String handleFolderNotFound(FolderNotFoundException e, RedirectAttributes redirectAttributes) {
        log.info("Folder not found: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("errorCode", HttpStatus.NOT_FOUND.value());
        redirectAttributes.addFlashAttribute("errorMessage", "Folder not found. " + e.getMessage());
        return "redirect:/error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ignoredE, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorCode", HttpStatus.FORBIDDEN.value()); // 403
        redirectAttributes.addFlashAttribute(
                "errorMessage", "You do not have permission to access this resource.");
        return "redirect:/error";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNoResourceFound(NoResourceFoundException ignoredE, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorCode", HttpStatus.NOT_FOUND.value()); // 404
        redirectAttributes.addFlashAttribute(
                "errorMessage", "The page does not exist.");
        return "redirect:/error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception e, RedirectAttributes redirectAttributes) {
        log.error("Unexpected error occurred", e);
        redirectAttributes.addFlashAttribute("errorCode", HttpStatus.INTERNAL_SERVER_ERROR.value()); // 500
        redirectAttributes.addFlashAttribute(
                "errorMessage", "An unexpected error occurred. Please try again later.");
        return "redirect:/error";
    }
}
