package com.planb.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("local")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Container
    static final MySQLContainer mysql =
            new MySQLContainer("mysql:8.4")
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                mysql::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                mysql::getUsername
        );

        registry.add(
                "spring.datasource.password",
                mysql::getPassword
        );

        registry.add(
                "spring.datasource.driver-class-name",
                mysql::getDriverClassName
        );
        registry.add(
                "spring.data.redis.host",
                redis::getHost
        );

        registry.add(
                "spring.data.redis.port",
                () -> redis.getMappedPort(6379)
        );

        registry.add(
                "jwt.secret",
                ()->"test-secret-key-must-be-at-least-32-bytes-long"
        );

        registry.add(
                "spring.ai.openai.api-key",
                () -> "test-openai-api-key"
        );

        registry.add(
                "spring.ai.openai.chat.model",
                () -> "gpt-4.1-mini"
        );

        registry.add(
                "external.food-ntr-cpnt.base-url",
                () -> "http://localhost"
        );
        registry.add(
                "external.food-ntr-cpnt.service-key",
                () -> "test-service-key"
        );

        registry.add(
                "external.kor2-service.base-url",
                () -> "http://localhost"
        );
        registry.add(
                "external.kor2-service.service-key",
                () -> "test-service-key"
        );

        registry.add(
                "external.tar-rlte-tar-service.base-url",
                () -> "http://localhost"
        );
        registry.add(
                "external.tar-rlte-tar-service.service-key",
                () -> "test-service-key"
        );
    }

    @BeforeEach
    void setUp() {

    }
}
