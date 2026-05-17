package ru.truemarket.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Тело POST /api/v1/auth/login (по docs/api/openapi.yaml, TASK-103). */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
