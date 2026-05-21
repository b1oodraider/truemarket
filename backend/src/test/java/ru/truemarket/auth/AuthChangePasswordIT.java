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
 * IT JWT-аутентификации + RBAC + /change-password (TASK-106). Полный контекст + Testcontainers PG.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthChangePasswordIT {

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

  private static final Pattern ACCESS = Pattern.compile("\"access_token\":\"([^\"]+)\"");
  private static final Pattern REFRESH = Pattern.compile("\"refresh_token\":\"([^\"]+)\"");
  private static final String PASSWORD = "verylongpassword12";

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (bearer != null) {
      builder.header("Authorization", "Bearer " + bearer);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String group(Pattern p, String body) {
    Matcher m = p.matcher(body);
    assertThat(m.find()).isTrue();
    return m.group(1);
  }

  private String accessToken;
  private String refreshToken;

  @BeforeEach
  void register() throws Exception {
    var r =
        post(
            "/api/v1/auth/register",
            "{\"email\":\"cp-"
                + System.nanoTime()
                + "@example.com\",\"password\":\""
                + PASSWORD
                + "\",\"accept_pdn_consent\":true}",
            null);
    assertThat(r.statusCode()).isEqualTo(201);
    accessToken = group(ACCESS, r.body());
    refreshToken = group(REFRESH, r.body());
  }

  @Test
  void changePassword_withoutToken_returns401() throws Exception {
    var r =
        post(
            "/api/v1/auth/change-password",
            "{\"current_password\":\"" + PASSWORD + "\",\"new_password\":\"brandnewpass99x\"}",
            null);
    assertThat(r.statusCode()).isEqualTo(401);
    assertThat(r.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
  }

  @Test
  void changePassword_wrongCurrent_returns400() throws Exception {
    var r =
        post(
            "/api/v1/auth/change-password",
            "{\"current_password\":\"totally-wrong-1\",\"new_password\":\"brandnewpass99x\"}",
            accessToken);
    assertThat(r.statusCode()).isEqualTo(400);
  }

  @Test
  void changePassword_success_returns204_andRevokesRefreshTokens() throws Exception {
    var changed =
        post(
            "/api/v1/auth/change-password",
            "{\"current_password\":\"" + PASSWORD + "\",\"new_password\":\"brandnewpass99x\"}",
            accessToken);
    assertThat(changed.statusCode()).isEqualTo(204);

    // refresh-токены отозваны (logout everywhere)
    var refresh =
        post("/api/v1/auth/refresh", "{\"refresh_token\":\"" + refreshToken + "\"}", null);
    assertThat(refresh.statusCode()).isEqualTo(401);
  }

  @Test
  void actuatorHealth_isPublic() throws Exception {
    var r =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    // permitAll (catch-all chain common): эндпоинт достижим без токена — не 401/403.
    // Конкретный 200/503 зависит от внешних компонентов (Redis/RabbitMQ), не от security.
    assertThat(r.statusCode()).isNotIn(401, 403);
  }
}
