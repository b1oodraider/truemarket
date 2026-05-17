package ru.truemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Интеграционный тест миграции auth-таблиц (TASK-101).
 *
 * <p>Проверяет что Flyway-миграции применяются на чистом Postgres и схема auth содержит все
 * ожидаемые таблицы, колонки и индексы строго по docs/erd.dbml.
 *
 * <p>Намеренно не использует @SpringBootTest: JPA-сущности появятся в TASK-102, а до этого
 * момента Hibernate ddl-auto: validate не может проверить схему. Тест работает через прямые
 * JDBC-запросы к information_schema и pg_catalog.
 */
@Testcontainers
class AuthMigrationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("truemarket_test")
          .withUsername("truemarket")
          .withPassword("truemarket");

  private static Connection conn;

  @BeforeAll
  static void applyMigrations() throws Exception {
    assertThatNoException()
        .isThrownBy(
            () ->
                Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate());

    conn =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  // ===== Table existence =====

  @Test
  void auth_users_table_exists() throws SQLException {
    assertThat(tableExists("auth", "users")).isTrue();
  }

  @Test
  void auth_refresh_tokens_table_exists() throws SQLException {
    assertThat(tableExists("auth", "refresh_tokens")).isTrue();
  }

  @Test
  void auth_user_consents_table_exists() throws SQLException {
    assertThat(tableExists("auth", "user_consents")).isTrue();
  }

  // ===== Column completeness (ERD) =====

  @Test
  void auth_users_has_all_required_columns() throws SQLException {
    assertThat(columns("auth", "users"))
        .containsExactlyInAnyOrder(
            "id",
            "email",
            "phone",
            "password_hash",
            "role",
            "email_verified",
            "phone_verified",
            "last_login_at",
            "created_at",
            "updated_at",
            "deleted_at",
            "version");
  }

  @Test
  void auth_refresh_tokens_has_all_required_columns() throws SQLException {
    assertThat(columns("auth", "refresh_tokens"))
        .containsExactlyInAnyOrder(
            "id", "user_id", "token_hash", "device_info", "expires_at", "revoked_at",
            "rotated_from", "created_at");
  }

  @Test
  void auth_user_consents_has_all_required_columns() throws SQLException {
    assertThat(columns("auth", "user_consents"))
        .containsExactlyInAnyOrder(
            "id", "user_id", "consent_type", "version", "accepted_at", "accepted_ip",
            "accepted_ua", "revoked_at");
  }

  // ===== Enum =====

  @Test
  void user_role_enum_type_exists() throws SQLException {
    try (var stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT typname FROM pg_type WHERE typtype = 'e' AND typname = 'user_role'")) {
      assertThat(rs.next()).as("enum user_role must exist in pg_type").isTrue();
    }
  }

  @Test
  void user_role_enum_has_correct_values_in_order() throws SQLException {
    try (var stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT e.enumlabel"
                    + " FROM pg_enum e"
                    + " JOIN pg_type t ON t.oid = e.enumtypid"
                    + " WHERE t.typname = 'user_role'"
                    + " ORDER BY e.enumsortorder")) {
      var values = new ArrayList<String>();
      while (rs.next()) {
        values.add(rs.getString(1));
      }
      assertThat(values).containsExactly("buyer", "seller", "moderator", "admin");
    }
  }

  // ===== Indexes =====

  @Test
  void auth_users_has_all_indexes() throws SQLException {
    assertThat(indexes("auth", "users"))
        .contains("idx_users_email", "idx_users_phone", "idx_users_role_deleted");
  }

  @Test
  void auth_refresh_tokens_has_all_indexes() throws SQLException {
    assertThat(indexes("auth", "refresh_tokens"))
        .contains("idx_refresh_tokens_user_id", "idx_refresh_tokens_expires_at");
  }

  @Test
  void auth_user_consents_has_all_indexes() throws SQLException {
    assertThat(indexes("auth", "user_consents"))
        .contains("idx_user_consents_user_consent", "idx_user_consents_type_version");
  }

  // ===== Constraints =====

  @Test
  void auth_users_email_unique_constraint_exists() throws SQLException {
    try (var stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT 1 FROM information_schema.table_constraints"
                    + " WHERE table_schema = 'auth'"
                    + " AND table_name = 'users'"
                    + " AND constraint_type = 'UNIQUE'"
                    + " AND constraint_name = 'users_email_key'")) {
      assertThat(rs.next()).as("UNIQUE constraint users_email_key must exist").isTrue();
    }
  }

  @Test
  void refresh_tokens_self_fk_on_rotated_from_exists() throws SQLException {
    try (var stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT 1 FROM information_schema.referential_constraints"
                    + " WHERE constraint_name = 'refresh_tokens_rotated_from_fkey'")) {
      assertThat(rs.next())
          .as("self-referencing FK refresh_tokens_rotated_from_fkey must exist")
          .isTrue();
    }
  }

  // ===== JDBC helpers =====

  private boolean tableExists(String schema, String table) throws SQLException {
    try (var stmt = conn.prepareStatement(
        "SELECT 1 FROM information_schema.tables"
            + " WHERE table_schema = ? AND table_name = ?")) {
      stmt.setString(1, schema);
      stmt.setString(2, table);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    }
  }

  private List<String> columns(String schema, String table) throws SQLException {
    var result = new ArrayList<String>();
    try (var stmt = conn.prepareStatement(
        "SELECT column_name FROM information_schema.columns"
            + " WHERE table_schema = ? AND table_name = ?")) {
      stmt.setString(1, schema);
      stmt.setString(2, table);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          result.add(rs.getString(1));
        }
      }
    }
    return result;
  }

  private List<String> indexes(String schema, String table) throws SQLException {
    var result = new ArrayList<String>();
    try (var stmt = conn.prepareStatement(
        "SELECT indexname FROM pg_indexes WHERE schemaname = ? AND tablename = ?")) {
      stmt.setString(1, schema);
      stmt.setString(2, table);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          result.add(rs.getString(1));
        }
      }
    }
    return result;
  }
}
