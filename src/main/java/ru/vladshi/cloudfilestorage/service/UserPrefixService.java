package ru.vladshi.cloudfilestorage.service;

public interface UserPrefixService {

    String buildUserPrefix(Long userId, String username);
}
