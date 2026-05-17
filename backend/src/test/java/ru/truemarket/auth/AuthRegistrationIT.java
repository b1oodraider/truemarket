package ru.truemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.truemarket.auth.repository.UserRepository;

/**
 * Интеграционный тест регистрации (TASK-102). Полный Spring-контекст + Testcontainers PG 16; Flyway
 * применяет миграции TASK-101 (схема — single source of truth Flyway, ADR-007, ddl-auto=none).
 *
 * <p>HTTP — JDK HttpClient + {@code local.server.port} (version-независимо к перестановкам
 * тест-модулей Spring Boot 4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthRegistrationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("truemarket_test")
          .withUsername("truemarket")
          .withPassword("truemarket");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Value("${local.server.port}")
  int port;

  @Autowired private UserRepository users;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> register(String body) throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void validRegistration_returns201_persistsUserAndConsent() throws Exception {
    var resp =
        register(
            "{\"email\":\"buyer1@example.com\",\"phone\":\"+79990000001\","
                + "\"password\":\"verylongpassword12\",\"accept_pdn_consent\":true}");

    assertThat(resp.statusCode()).isEqualTo(201);
    String body = resp.body();
    assertThat(body).contains("\"token_type\":\"Bearer\"").contains("\"expires_in\":900");
    assertThat(body).containsPattern("\"access_token\":\"[^\"]+\"");
    assertThat(body).containsPattern("\"refresh_token\":\"[^\"]+\"");
    assertThat(users.existsByEmail("buyer1@example.com")).isTrue();
  }

  @Test
  void duplicateEmail_caseInsensitive_returns409Problem() throws Exception {
    assertThat(
            register(
                    "{\"email\":\"dup@example.com\",\"password\":\"verylongpassword12\","
                        + "\"accept_pdn_consent\":true}")
                .statusCode())
        .isEqualTo(201);

    var conflict =
        register(
            "{\"email\":\"DUP@Example.com\",\"password\":\"anotherlongpass34\","
                + "\"accept_pdn_consent\":true}");
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(conflict.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
    assertThat(conflict.body()).contains("trace_id");
  }

  @Test
  void shortPassword_returns400Problem() throws Exception {
    var resp =
        register(
            "{\"email\":\"shortpw@example.com\",\"password\":\"short\","
                + "\"accept_pdn_consent\":true}");
    assertThat(resp.statusCode()).isEqualTo(400);
    assertThat(resp.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }

  @Test
  void pdnConsentNotAccepted_returns400_andNoUser() throws Exception {
    var resp =
        register(
            "{\"email\":\"noconsent@example.com\",\"password\":\"verylongpassword12\","
                + "\"accept_pdn_consent\":false}");
    assertThat(resp.statusCode()).isEqualTo(400);
    assertThat(users.existsByEmail("noconsent@example.com")).isFalse();
  }

  @Test
  void invalidEmail_returns400() throws Exception {
    var resp =
        register(
            "{\"email\":\"not-an-email\",\"password\":\"verylongpassword12\","
                + "\"accept_pdn_consent\":true}");
    assertThat(resp.statusCode()).isEqualTo(400);
  }
}
