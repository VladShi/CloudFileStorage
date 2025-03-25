package ru.vladshi.cloudfilestorage.storage.service;

public interface UserPrefixService {

    String buildUserPrefix(Long userId, String username);
}
