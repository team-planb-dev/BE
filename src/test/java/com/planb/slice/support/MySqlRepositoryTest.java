package com.planb.slice.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * QueryRepository 슬라이스 테스트에 독립 MySQL 환경을 제공한다.
 */
public abstract class MySqlRepositoryTest {

    private static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4")
                    .withDatabaseName("planb_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerMySqlProperties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "spring.datasource.url",
                MYSQL::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                MYSQL::getUsername
        );

        registry.add(
                "spring.datasource.password",
                MYSQL::getPassword
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "create"
        );

        registry.add(
                "spring.flyway.enabled",
                () -> "false"
        );
    }
}
