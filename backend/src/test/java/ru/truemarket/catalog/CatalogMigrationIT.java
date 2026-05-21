package ru.truemarket.catalog;

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
 * Интеграционный тест миграции catalog-таблиц (TASK-108).
 *
 * <p>Проверяет, что Flyway применяется на чистом Postgres и схема catalog содержит таблицы,
 * колонки, индексы и ключевые ограничения строго по docs/erd.dbml. Прямой JDBC
 * (без @SpringBootTest) — JPA-сущности появятся в CRUD-задачах (TASK-109/110), как в {@code
 * AuthMigrationIT}.
 */
@Testcontainers
class CatalogMigrationIT {

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
                    .dataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate());

    conn =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  // ===== Table existence =====

  @Test
  void catalog_tables_exist() throws SQLException {
    assertThat(tableExists("catalog", "categories")).isTrue();
    assertThat(tableExists("catalog", "products")).isTrue();
    assertThat(tableExists("catalog", "product_images")).isTrue();
  }

  // ===== Column completeness (ERD) =====

  @Test
  void categories_has_all_required_columns() throws SQLException {
    assertThat(columns("catalog", "categories"))
        .containsExactlyInAnyOrder(
            "id",
            "parent_id",
            "slug",
            "name",
            "commission_base",
            "requires_marking",
            "sort_order",
            "created_at",
            "updated_at");
  }

  @Test
  void products_has_all_required_columns() throws SQLException {
    assertThat(columns("catalog", "products"))
        .containsExactlyInAnyOrder(
            "id",
            "seller_id",
            "category_id",
            "title",
            "description",
            "price_amount",
            "price_currency",
            "stock",
            "requires_marking",
            "gtin",
            "status",
            "verified_at",
            "created_at",
            "updated_at",
            "deleted_at",
            "version");
  }

  @Test
  void product_images_has_all_required_columns() throws SQLException {
    assertThat(columns("catalog", "product_images"))
        .containsExactlyInAnyOrder(
            "id",
            "product_id",
            "s3_key",
            "alt_text",
            "width",
            "height",
            "size_bytes",
            "position",
            "is_primary",
            "created_at");
  }

  // ===== Indexes (ERD) =====

  @Test
  void catalog_has_expected_indexes() throws SQLException {
    assertThat(indexes("catalog", "categories")).contains("idx_categories_parent_id");
    assertThat(indexes("catalog", "products"))
        .contains(
            "idx_products_seller_id",
            "idx_products_category_id",
            "idx_products_status_deleted",
            "idx_products_gtin");
    assertThat(indexes("catalog", "product_images"))
        .contains("idx_product_images_product_position");
  }

  // ===== Constraints =====

  @Test
  void categories_self_fk_on_parent_id_exists() throws SQLException {
    assertThat(referentialConstraintExists("categories_parent_id_fkey")).isTrue();
  }

  @Test
  void products_category_fk_exists() throws SQLException {
    assertThat(referentialConstraintExists("products_category_id_fkey")).isTrue();
  }

  @Test
  void product_images_product_fk_cascades() throws SQLException {
    try (var stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT delete_rule FROM information_schema.referential_constraints"
                    + " WHERE constraint_name = 'product_images_product_id_fkey'")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("delete_rule")).isEqualTo("CASCADE");
    }
  }

  @Test
  void products_status_check_constraint_exists() throws SQLException {
    try (var stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT 1 FROM information_schema.check_constraints"
                    + " WHERE constraint_name = 'products_status_check'")) {
      assertThat(rs.next()).as("CHECK products_status_check must exist").isTrue();
    }
  }

  @Test
  void products_seller_id_has_no_fk_yet() throws SQLException {
    // FK на verification.sellers добавляется в TASK-114 (expand-contract) — здесь его быть не
    // должно.
    try (var stmt = conn.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT 1 FROM information_schema.key_column_usage k"
                    + " JOIN information_schema.referential_constraints r"
                    + "   ON k.constraint_name = r.constraint_name"
                    + " WHERE k.table_schema = 'catalog' AND k.table_name = 'products'"
                    + "   AND k.column_name = 'seller_id'")) {
      assertThat(rs.next()).as("seller_id FK не должен существовать до TASK-114").isFalse();
    }
  }

  // ===== JDBC helpers =====

  private boolean referentialConstraintExists(String name) throws SQLException {
    try (var stmt =
        conn.prepareStatement(
            "SELECT 1 FROM information_schema.referential_constraints WHERE constraint_name = ?")) {
      stmt.setString(1, name);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    }
  }

  private boolean tableExists(String schema, String table) throws SQLException {
    try (var stmt =
        conn.prepareStatement(
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
    try (var stmt =
        conn.prepareStatement(
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
    try (var stmt =
        conn.prepareStatement(
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
