package com.smartaps.module.job;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class JobSchemaMigrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Test
    void migrationAddsJobViewCountAndLogicalDeleteToTheV5Schema() throws SQLException {
        configureFlyway()
                .target(MigrationVersion.fromVersion("5"))
                .load()
                .migrate();

        try (Connection connection = openConnection()) {
            assertThat(columnExists(connection, "view_count")).isFalse();
            assertThat(columnExists(connection, "deleted")).isFalse();
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO jobs (title, description, requirements, creator_id)
                        VALUES ('Existing job', 'Description', 'Requirements', 1)
                        """);
            }
        }

        configureFlyway().load().migrate();

        try (Connection connection = openConnection()) {
            assertColumnDefinition(connection, "view_count", "int");
            assertColumnDefinition(connection, "deleted", "tinyint");

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT view_count, deleted FROM jobs WHERE title = ?")) {
                statement.setString(1, "Existing job");
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt("view_count")).isZero();
                    assertThat(resultSet.getInt("deleted")).isZero();
                }
            }
        }
    }

    private FluentConfiguration configureFlyway() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration");
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'jobs'
                  AND column_name = ?
                """)) {
            statement.setString(1, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private void assertColumnDefinition(Connection connection, String columnName, String expectedType)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'jobs'
                  AND column_name = ?
                """)) {
            statement.setString(1, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("data_type")).isEqualTo(expectedType);
                assertThat(resultSet.getString("is_nullable")).isEqualTo("NO");
                assertThat(resultSet.getString("column_default")).isEqualTo("0");
            }
        }
    }
}
