package ru.vladshi.cloudfilestorage.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.vladshi.cloudfilestorage.dto.UserDto;

@Controller
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/login")
    public String showLoginForm(@ModelAttribute("userDto") UserDto userDto) {
        return "login";
    }
}
