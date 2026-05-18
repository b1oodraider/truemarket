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
 * Интеграционный тест персистентной ротации + replay-detection + /logout (TASK-104). Полный
 * контекст + Testcontainers PG 16.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthRefreshRotationIT {

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

  private static String refreshOf(String body) {
    Matcher m = REFRESH.matcher(body);
    assertThat(m.find()).isTrue();
    return m.group(1);
  }

  private String refreshToken;

  @BeforeEach
  void register() throws Exception {
    var r =
        post(
            "/api/v1/auth/register",
            "{\"email\":\"rot-"
                + System.nanoTime()
                + "@example.com\",\"password\":\"verylongpassword12\","
                + "\"accept_pdn_consent\":true}");
    assertThat(r.statusCode()).isEqualTo(201);
    refreshToken = refreshOf(r.body());
  }

  @Test
  void refresh_rotatesAndReplayRevokesChain() throws Exception {
    var first = post("/api/v1/auth/refresh", "{\"refresh_token\":\"" + refreshToken + "\"}");
    assertThat(first.statusCode()).isEqualTo(200);
    String newRefresh = refreshOf(first.body());

    // Повторное использование старого (уже ротированного) → replay → 401
    var replay = post("/api/v1/auth/refresh", "{\"refresh_token\":\"" + refreshToken + "\"}");
    assertThat(replay.statusCode()).isEqualTo(401);

    // Цепочка отозвана: даже валидный новый refresh больше не работает
    var afterReplay = post("/api/v1/auth/refresh", "{\"refresh_token\":\"" + newRefresh + "\"}");
    assertThat(afterReplay.statusCode()).isEqualTo(401);
  }

  @Test
  void logout_revokesRefresh_thenRefreshFails_idempotent() throws Exception {
    var out = post("/api/v1/auth/logout", "{\"refresh_token\":\"" + refreshToken + "\"}");
    assertThat(out.statusCode()).isEqualTo(204);

    var afterLogout = post("/api/v1/auth/refresh", "{\"refresh_token\":\"" + refreshToken + "\"}");
    assertThat(afterLogout.statusCode()).isEqualTo(401);

    // Идемпотентно: повторный logout тем же (или неизвестным) токеном — тоже 204
    var again = post("/api/v1/auth/logout", "{\"refresh_token\":\"" + refreshToken + "\"}");
    assertThat(again.statusCode()).isEqualTo(204);
  }
}
