package ru.vladshi.cloudfilestorage.user.mapper;

import ru.vladshi.cloudfilestorage.user.dto.UserDto;
import ru.vladshi.cloudfilestorage.user.entity.User;

public class UserMapper {

    private UserMapper() {
    }

    public static User toEntity(UserDto dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        return user;
    }
}
