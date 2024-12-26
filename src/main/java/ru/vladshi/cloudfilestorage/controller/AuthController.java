package ru.vladshi.cloudfilestorage.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.vladshi.cloudfilestorage.dto.UserDto;
import ru.vladshi.cloudfilestorage.entity.User;
import ru.vladshi.cloudfilestorage.exception.UserRegistrationException;
import ru.vladshi.cloudfilestorage.mapper.UserMapper;
import ru.vladshi.cloudfilestorage.service.UserService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    @GetMapping("/register")
    public String showRegistrationForm(@ModelAttribute("userDto") UserDto userDto) {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(HttpServletRequest request,
                               @ModelAttribute("userDto") @Valid UserDto userDto,
                               BindingResult bindingResult) {

        if (!bindingResult.hasErrors()) {
            User user = UserMapper.toEntity(userDto);
            saveUser(user, bindingResult);
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        autoLogin(request, userDto);

        return "redirect:/";
    }

    @GetMapping("/login")
    public String showLoginForm(@ModelAttribute("userDto") UserDto userDto) {
        return "login";
    }

    private void saveUser(User user, BindingResult bindingResult) {
        try {
            userService.save(user);
        } catch (UserRegistrationException e) {
            bindingResult.rejectValue("username", "error.username", e.getMessage());
        }
    }

    private static void autoLogin(HttpServletRequest request, UserDto userDto) {
        try {
            request.login(userDto.getUsername(), userDto.getPassword());
        } catch (ServletException e) {
            // LOGGER.error("Error while login ", e);
            System.out.println("Error while login");
        }
    }
}
