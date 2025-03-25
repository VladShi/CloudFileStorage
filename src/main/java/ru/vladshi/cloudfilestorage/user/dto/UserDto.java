package ru.vladshi.cloudfilestorage.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter @Getter
public class UserDto {
    @NotNull
    @NotEmpty(message = "Username is required")
    @Size(min = 4, max = 30, message = "Username must be between 4 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9!@$^_-]*$",
            message = "Username can only contain English letters, numbers, and special characters !@$^_-")
    private String username;

    @NotNull
    @NotEmpty(message = "Password is required")
    @Size(min = 4, max = 20, message = "Password must be between 4 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9!@#$%^&*_-]*$",
            message = "Password can only contain English letters, numbers, and special characters !@#$%^&*_-")
    private String password;
}
