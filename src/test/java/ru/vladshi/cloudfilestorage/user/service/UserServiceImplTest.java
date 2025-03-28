package ru.vladshi.cloudfilestorage.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import ru.vladshi.cloudfilestorage.storage.service.FolderService;
import ru.vladshi.cloudfilestorage.storage.service.UserPrefixService;
import ru.vladshi.cloudfilestorage.user.entity.User;
import ru.vladshi.cloudfilestorage.user.exception.UserRegistrationException;
import ru.vladshi.cloudfilestorage.user.repository.UserRepository;
import ru.vladshi.cloudfilestorage.user.service.impl.UserServiceImpl;

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

    private static User testUser;
    private final static String TEST_USERNAME = "testusername";
    private final static String TEST_PASSWORD = "testpassword";
    private static final String TEST_USER_PREFIX = "1-testusername/";

    @Configuration
    @EnableJpaRepositories(basePackages = "ru.vladshi.cloudfilestorage.user.repository")
    @EntityScan(basePackages = "ru.vladshi.cloudfilestorage.user.entity")
    static class TestConfig {
        @Bean
        public UserService userService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                       UserPrefixService userPrefixService, FolderService folderService) {
            return new UserServiceImpl(userRepository, passwordEncoder, userPrefixService, folderService);
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        public UserPrefixService userPrefixService() {
            UserPrefixService mock = Mockito.mock(UserPrefixService.class);
            Mockito.when(mock.buildUserPrefix(1L, "testusername")).thenReturn(TEST_USER_PREFIX);
            return mock;
        }

        @Bean
        public FolderService folderService() throws Exception {
            FolderService mock = Mockito.mock(FolderService.class);
            Mockito.doNothing().when(mock).createUserRootFolder(Mockito.anyString());
            return mock;
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

        testUser = new User();
        testUser.setUsername(TEST_USERNAME);
        testUser.setPassword(TEST_PASSWORD);
    }

    @Test
    @DisplayName("Успешная регистрация пользователя")
    public void shouldRegisterUserSuccessfully() {

        userService.registerUser(testUser);

        User savedUser = userRepository.findByUsername(TEST_USERNAME);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getUsername()).isEqualTo(TEST_USERNAME);
    }

    @Test
    @DisplayName("Регистрация пользователя с уже используемым именем")
    public void shouldThrowUserRegistrationExceptionWhenRegisteringUserWithDuplicateUsername() {

        userService.registerUser(testUser);
        User userWithDuplicateUsername = new User();
        userWithDuplicateUsername.setUsername(TEST_USERNAME);
        userWithDuplicateUsername.setPassword("password2");

        assertThrows(UserRegistrationException.class, () -> userService.registerUser(userWithDuplicateUsername),
                " Должно выброситься исключение при попытке регистрации пользователя с уже занятым именем");
    }
}
