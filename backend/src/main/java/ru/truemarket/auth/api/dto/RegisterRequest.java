package ru.truemarket.auth.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Тело POST /api/v1/auth/register (строго по docs/api/openapi.yaml, TASK-102).
 *
 * <p>Пароль ≥ 12 символов (CLAUDE.md §13.1). {@code accept_pdn_consent} обязателен и должен быть
 * true — 152-ФЗ (без согласия регистрация запрещена).
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "phone must match ^\\+?[0-9]{10,15}$")
        String phone,
    @NotBlank @Size(min = 12, max = 256, message = "password must be at least 12 characters")
        String password,
    @JsonProperty("accept_pdn_consent")
        @AssertTrue(message = "accept_pdn_consent must be true (152-FZ)")
        boolean acceptPdnConsent) {}
