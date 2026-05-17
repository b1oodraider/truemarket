package ru.truemarket.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ответ с парой токенов (строго по docs/api/openapi.yaml, TASK-102).
 *
 * <p>{@code expiresIn} — секунды до истечения access-токена (TTL ≤ 15 мин, CLAUDE.md §13.1).
 */
public record TokenPair(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("token_type") String tokenType) {

  public static TokenPair bearer(String accessToken, String refreshToken, long expiresInSeconds) {
    return new TokenPair(accessToken, refreshToken, expiresInSeconds, "Bearer");
  }
}
