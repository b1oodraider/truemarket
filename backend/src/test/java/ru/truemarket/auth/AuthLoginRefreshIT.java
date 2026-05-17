package ru.truemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Интеграционный тест /login + /refresh (TASK-103). Полный контекст + Testcontainers PG 16.
 * Пользователь создаётся через /register (TASK-102). HTTP — JDK HttpClient (version-независимо).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthLoginRefreshIT {

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

  private static final Pattern REFRESH = Pattern.compile("\"refresh_token\":\"([^\"]+)\"");

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> post(String path, String body) throws Exception {
    return http.send(
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private String email;

  @BeforeEach
  void registerUser() throws Exception {
    email = "login-" + System.nanoTime() + "@example.com";
    var r =
        post(
            "/api/v1/auth/register",
            "{\"email\":\""
                + email
                + "\",\"password\":\"verylongpassword12\","
                + "\"accept_pdn_consent\":true}");
    assertThat(r.statusCode()).isEqualTo(201);
  }

  @Test
  void login_validCredentials_returns200TokenPair() throws Exception {
    var r =
        post(
            "/api/v1/auth/login",
            "{\"email\":\"" + email + "\",\"password\":\"verylongpassword12\"}");
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.body())
        .contains("\"token_type\":\"Bearer\"")
        .containsPattern("\"access_token\":\"[^\"]+\"")
        .containsPattern("\"refresh_token\":\"[^\"]+\"");
  }

  @Test
  void login_wrongPassword_returns401() throws Exception {
    var r =
        post(
            "/api/v1/auth/login",
            "{\"email\":\"" + email + "\",\"password\":\"wrong-password-xx\"}");
    assertThat(r.statusCode()).isEqualTo(401);
    assertThat(r.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }

  @Test
  void login_unknownEmail_returns401_sameResponse() throws Exception {
    var r =
        post(
            "/api/v1/auth/login",
            "{\"email\":\"nobody-xyz@example.com\",\"password\":\"verylongpassword12\"}");
    assertThat(r.statusCode()).isEqualTo(401);
  }

  @Test
  void refresh_validRefreshToken_returns200NewPair() throws Exception {
    var login =
        post(
            "/api/v1/auth/login",
            "{\"email\":\"" + email + "\",\"password\":\"verylongpassword12\"}");
    Matcher m = REFRESH.matcher(login.body());
    assertThat(m.find()).isTrue();
    String refreshToken = m.group(1);

    var r = post("/api/v1/auth/refresh", "{\"refresh_token\":\"" + refreshToken + "\"}");
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.body()).containsPattern("\"access_token\":\"[^\"]+\"");
  }

  @Test
  void refresh_garbageToken_returns401() throws Exception {
    var r = post("/api/v1/auth/refresh", "{\"refresh_token\":\"not.a.jwt\"}");
    assertThat(r.statusCode()).isEqualTo(401);
  }
}
