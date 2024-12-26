package ru.vladshi.cloudfilestorage;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final String MYSQL_IMAGE = "mysql:9.1.0";

	@Bean
	@ServiceConnection
	MySQLContainer<?> mysqlContainer() {
		return new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
				.withDatabaseName("testdb")
				.withUsername("test")
				.withPassword("test")
				.withCreateContainerCmdModifier(
						cmd -> cmd.withName("mysql-test"));
	}

}
