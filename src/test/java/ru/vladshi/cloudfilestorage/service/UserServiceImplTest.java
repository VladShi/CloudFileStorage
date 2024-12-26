package ru.vladshi.cloudfilestorage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vladshi.cloudfilestorage.TestcontainersConfiguration;
import ru.vladshi.cloudfilestorage.entity.User;
import ru.vladshi.cloudfilestorage.exception.UserRegistrationException;
import ru.vladshi.cloudfilestorage.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = TestcontainersConfiguration.class)
@Testcontainers
public class UserServiceImplTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MySQLContainer<?> mysqlContainer;

    private static User testuser;
    private final static String TEST_USERNAME = "testusername";
    private final static String TEST_PASSWORD = "testpassword";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testuser = new User();
        testuser.setUsername(TEST_USERNAME);
        testuser.setPassword(TEST_PASSWORD);
    }

    @Test
    @DisplayName("Should save user successfully")
    public void shouldSaveUserSuccessfully() {

        userService.save(testuser);

        assertThat(userRepository.findByUsername(TEST_USERNAME)).isNotNull();
    }

    @Test
    @DisplayName("Should throw UserRegistrationException when saving user with duplicate username")
    public void shouldThrowUserRegistrationExceptionWhenSavingUserWithDuplicateUsername() {

        userService.save(testuser);

        User userWithDuplicateUsername = new User();
        userWithDuplicateUsername.setUsername(TEST_USERNAME);
        userWithDuplicateUsername.setPassword("password2");

        assertThrows(UserRegistrationException.class, () -> userService.save(userWithDuplicateUsername));
    }

    @Test
    @DisplayName("Should load user by username successfully")
    public void shouldLoadUserByUsernameSuccessfully() {

        userService.save(testuser);

        UserDetails userDetails = userService.loadUserByUsername(TEST_USERNAME);


        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(userDetails.getPassword()).isEqualTo(testuser.getPassword());
        assertThat(userDetails.getAuthorities()).isEmpty();
    }
}
