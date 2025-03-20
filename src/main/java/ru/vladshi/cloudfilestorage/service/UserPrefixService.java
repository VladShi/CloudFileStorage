package ru.vladshi.cloudfilestorage.service;

public interface UserPrefixService {

    String generate(Long userId, String username);

}
