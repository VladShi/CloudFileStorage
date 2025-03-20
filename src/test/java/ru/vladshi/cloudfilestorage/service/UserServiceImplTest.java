package ru.vladshi.cloudfilestorage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.vladshi.cloudfilestorage.entity.User;
import ru.vladshi.cloudfilestorage.exception.UserRegistrationException;
import ru.vladshi.cloudfilestorage.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ContextConfiguration(classes = UserServiceImplTest.TestConfig.class)
@Testcontainers
public class UserServiceImplTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private static final String MYSQL_IMAGE = "mysql:9.1.0";

    @Container
    protected static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
//			.withReuse(true) // убрать после создания тестов, вернуть управление @Container
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
//            .withCreateContainerCmdModifier(
//                    cmd -> cmd.withName("mysql-test"))
            ;

    private static User testuser;
    private final static String TEST_USERNAME = "testusername";
    private final static String TEST_PASSWORD = "testpassword";

    @Configuration
    @EnableJpaRepositories(basePackages = "ru.vladshi.cloudfilestorage.repository")
    @EntityScan(basePackages = "ru.vladshi.cloudfilestorage.entity")
    static class TestConfig {
        @Bean
        public UserService userService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
            return new UserServiceImpl(userRepository, passwordEncoder);
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {

//        mysqlContainer.start(); // убрать после создания тестов, вернуть управление @Container

        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
    }

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

//    @Test // TODO убрать
//    @DisplayName("Should load user by username successfully")
//    public void shouldLoadUserByUsernameSuccessfully() {
//
//        userService.registerUser(testuser);
//
//        UserDetails userDetails = userService.loadUserByUsername(TEST_USERNAME);
//
//
//        assertThat(userDetails).isNotNull();
//        assertThat(userDetails.getUsername()).isEqualTo(TEST_USERNAME);
//        assertThat(userDetails.getPassword()).isEqualTo(testuser.getPassword());
//        assertThat(userDetails.getAuthorities()).isEmpty();
//    }
}
