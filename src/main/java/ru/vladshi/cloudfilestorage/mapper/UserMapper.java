package ru.vladshi.cloudfilestorage.mapper;

import ru.vladshi.cloudfilestorage.dto.UserDto;
import ru.vladshi.cloudfilestorage.entity.User;

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
