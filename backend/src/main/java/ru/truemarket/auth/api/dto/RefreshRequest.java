package ru.truemarket.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Тело POST /api/v1/auth/refresh (по docs/api/openapi.yaml, TASK-103). */
public record RefreshRequest(@JsonProperty("refresh_token") @NotBlank String refreshToken) {}
