package ru.truemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * IT распределённого rate-limit (TASK-107): Testcontainers PG + Redis, {@code
 * rate-limit.enabled=true} (переопределяет тест-профиль). Проверяет 429 + Retry-After.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthRateLimitIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("truemarket_test")
          .withUsername("truemarket")
          .withPassword("truemarket");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.data.redis.host", REDIS::getHost);
    r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    r.add("truemarket.rate-limit.enabled", () -> "true"); // переопределяем test-профиль
  }

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

  @Test
  void login_sixthFromSameIp_returns429WithRetryAfter() throws Exception {
    String body = "{\"email\":\"rl-login@example.com\",\"password\":\"wrongbutlongpw\"}";

    for (int i = 1; i <= 5; i++) {
      // лимит не исчерпан → штатная обработка (401, т.к. пользователя нет)
      assertThat(post("/api/v1/auth/login", body).statusCode()).isEqualTo(401);
    }
    HttpResponse<String> sixth = post("/api/v1/auth/login", body);
    assertThat(sixth.statusCode()).isEqualTo(429);
    assertThat(sixth.headers().firstValue("Retry-After")).isPresent();
  }

  @Test
  void register_fourthFromSameIp_returns429() throws Exception {
    for (int i = 1; i <= 3; i++) {
      var r =
          post(
              "/api/v1/auth/register",
              "{\"email\":\"rl-reg-"
                  + i
                  + "@example.com\",\"password\":\"verylongpassword12\",\"accept_pdn_consent\":true}");
      assertThat(r.statusCode()).isEqualTo(201);
    }
    var fourth =
        post(
            "/api/v1/auth/register",
            "{\"email\":\"rl-reg-4@example.com\",\"password\":\"verylongpassword12\",\"accept_pdn_consent\":true}");
    assertThat(fourth.statusCode()).isEqualTo(429);
    assertThat(fourth.headers().firstValue("Retry-After")).isPresent();
  }
}
