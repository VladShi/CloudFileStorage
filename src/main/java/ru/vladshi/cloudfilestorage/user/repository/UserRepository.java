package ru.vladshi.cloudfilestorage.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vladshi.cloudfilestorage.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
}
