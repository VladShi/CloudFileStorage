package ru.vladshi.cloudfilestorage.storage.service.impl;

import org.springframework.stereotype.Service;
import ru.vladshi.cloudfilestorage.storage.service.UserPrefixService;

@Service
public class UserPrefixServiceImpl implements UserPrefixService {

    @Override
    public String buildUserPrefix(Long userId, String username) {
        return userId + "-" + username + "/";
    }
}
