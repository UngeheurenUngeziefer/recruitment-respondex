package com.platform.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared PostgreSQL for integration tests (one database per JVM).
 * <p>
 * Setup mirrors production (see README "Run With PostgreSQL"):
 * 1. Flyway applies the migrations as the schema owner.
 * 2. db/roles.sql creates the restricted app_rt role and its grants.
 * 3. The Spring application connects as app_rt, so RLS policies apply to every query.
 * <p>
 * * Where the database comes from:
 * - Default: a PostgreSQL 16 Testcontainers container. Requires Docker.
 * - TEST_DB_URL set: an existing PostgreSQL instead (no Docker needed). Use a disposable database,
 *   because the tests create organizations and studies. Optional: TEST_DB_OWNER_USER (default
 *   recrut_owner) and TEST_DB_OWNER_PASSWORD. The owner role must be allowed to create roles.
 * - Neither available: the tests are skipped (see {@link TestDatabaseAvailability}).
 */
public abstract class AbstractPostgresIT {
    private static final String APP_USER = "app_rt";
    private static final String APP_PASSWORD = "app-test";
    private static final String DB_URL;
    private static final String OWNER_USER;
    private static final String OWNER_PASSWORD;
    private static final PostgreSQLContainer<?> CONTAINER;

    static {
        String externalUrl = System.getenv("TEST_DB_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            CONTAINER = null;
            DB_URL = externalUrl;
            OWNER_USER = orDefault(System.getenv("TEST_DB_OWNER_USER"), "recrut_owner");
            OWNER_PASSWORD = orDefault(System.getenv("TEST_DB_OWNER_PASSWORD"), "");
        } else {
            CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("recrut")
                    .withUsername("recrut_owner")
                    .withPassword("owner-test");
            CONTAINER.start();
            DB_URL = CONTAINER.getJdbcUrl();
            OWNER_USER = CONTAINER.getUsername();
            OWNER_PASSWORD = CONTAINER.getPassword();
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void migrate() {
        Flyway.configure()
                .dataSource(DB_URL, OWNER_USER, OWNER_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        // db/roles.sql is run from the project root (the Maven working directory). It is executed as
        // one statement: it contains $$-quoted DO blocks, which Spring's ScriptUtils cannot split.
        try (Connection connection = DriverManager.getConnection(DB_URL, OWNER_USER, OWNER_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("db/roles.sql")));
            // roles.sql creates the role without a password; the tests set one for the app connection.
            statement.execute("ALTER ROLE " + APP_USER + " PASSWORD '" + APP_PASSWORD + "'");
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Could not apply db/roles.sql to the test database", e);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        // Migrations already ran above as the owner; Spring's own Flyway would use the wrong role.
        registry.add("spring.flyway.enabled", () -> "false");
    }

    /**
     * Connects as the schema owner: bypasses RLS, used only to arrange and inspect test data.
     */
    protected static JdbcTemplate ownerJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(DB_URL, OWNER_USER, OWNER_PASSWORD));
    }

    protected static UUID insertOrganization(JdbcTemplate owner, String name) {
        UUID id = UUID.randomUUID();
        owner.update("insert into organization (id, name) values (?, ?)", id, name);
        return id;
    }

    protected static UUID insertUser(JdbcTemplate owner) {
        UUID id = UUID.randomUUID();
        owner.update("insert into app_user (id, email, name) values (?, ?, 'Test user')",
                id, id + "@example.test");
        return id;
    }
}