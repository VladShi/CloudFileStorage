package ru.vladshi.cloudfilestorage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vladshi.cloudfilestorage.BaseTestcontainersForTest;
import ru.vladshi.cloudfilestorage.entity.User;
import ru.vladshi.cloudfilestorage.exception.UserRegistrationException;
import ru.vladshi.cloudfilestorage.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
public class UserServiceImplTest extends BaseTestcontainersForTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

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
    @DisplayName("Should register user successfully")
    public void shouldRegisterUserSuccessfully() {

        userService.registerUser(testuser);

        assertThat(userRepository.findByUsername(TEST_USERNAME)).isNotNull();
    }

    @Test
    @DisplayName("Should throw UserRegistrationException when registering user with duplicate username")
    public void shouldThrowUserRegistrationExceptionWhenRegisteringUserWithDuplicateUsername() {

        userService.registerUser(testuser);

        User userWithDuplicateUsername = new User();
        userWithDuplicateUsername.setUsername(TEST_USERNAME);
        userWithDuplicateUsername.setPassword("password2");

        assertThrows(UserRegistrationException.class, () -> userService.registerUser(userWithDuplicateUsername));
    }

    @Test
    @DisplayName("Should load user by username successfully")
    public void shouldLoadUserByUsernameSuccessfully() {

        userService.registerUser(testuser);

        UserDetails userDetails = userService.loadUserByUsername(TEST_USERNAME);


        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(userDetails.getPassword()).isEqualTo(testuser.getPassword());
        assertThat(userDetails.getAuthorities()).isEmpty();
    }
}
