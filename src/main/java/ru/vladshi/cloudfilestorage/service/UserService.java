package ru.vladshi.cloudfilestorage.service;

import org.springframework.security.core.userdetails.UserDetailsService;
import ru.vladshi.cloudfilestorage.entity.User;

public interface UserService extends UserDetailsService {
    void registerUser(User user);
}
