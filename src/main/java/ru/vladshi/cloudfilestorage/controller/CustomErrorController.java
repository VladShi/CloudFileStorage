package ru.vladshi.cloudfilestorage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CustomErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    @RequestMapping("/error")
    public String handleError(WebRequest webRequest) {
        Map<String, Object> errorAttributes =
                this.errorAttributes.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults());
        Integer statusCode = (Integer) errorAttributes.get("status");

        HttpStatus httpStatus = HttpStatus.resolve(statusCode);

        if (httpStatus == null) {
            return "redirect:/";
        }

        return switch (httpStatus) {
            case FORBIDDEN -> "error/access-denied";  // 403
            case NOT_FOUND -> "error/error-404";
            case INTERNAL_SERVER_ERROR -> "error/error-500";
            default -> "error/error";
        };
    }
}
