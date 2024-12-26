package ru.vladshi.cloudfilestorage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vladshi.cloudfilestorage.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
}
