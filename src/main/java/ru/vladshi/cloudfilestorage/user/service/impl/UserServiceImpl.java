package ru.vladshi.cloudfilestorage.user.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.vladshi.cloudfilestorage.user.entity.User;
import ru.vladshi.cloudfilestorage.user.exception.UserRegistrationException;
import ru.vladshi.cloudfilestorage.user.repository.UserRepository;
import ru.vladshi.cloudfilestorage.storage.service.MinioService;
import ru.vladshi.cloudfilestorage.storage.service.UserPrefixService;
import ru.vladshi.cloudfilestorage.user.service.UserService;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPrefixService userPrefixService;
    private final MinioService minioService;

    @Override
    public void registerUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        try {
            userRepository.save(user);
            String userFolderName = userPrefixService.buildUserPrefix(user.getId(), user.getUsername());
            minioService.createUserRootFolder(userFolderName);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                throw new UserRegistrationException("Username is already taken");
            }
            throw e;
        } catch (Exception e) {
            log.error("Error during user registration: {}", e.getMessage(), e);
            userRepository.delete(user);
            throw new UserRegistrationException("Failed to register due to storage error");
        }
    }
}
