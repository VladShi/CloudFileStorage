package ru.vladshi.cloudfilestorage.service.impl;

import org.springframework.stereotype.Service;
import ru.vladshi.cloudfilestorage.service.UserPrefixService;

@Service
public class UserPrefixServiceImpl implements UserPrefixService {

    @Override
    public String generate(Long userId, String username) {
        return userId + "-" + username + "/";
    }
}
