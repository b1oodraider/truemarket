package ru.truemarket.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Тело POST /api/v1/auth/change-password (TASK-106).
 *
 * <p>{@code new_password} ≥ 12 символов (CLAUDE.md §13.1); дополнительно проверяется по базе утечек
 * (haveibeenpwned, TASK-105) уже в сервисе. Текущий пароль подтверждает владение аккаунтом.
 */
public record ChangePasswordRequest(
    @JsonProperty("current_password") @NotBlank String currentPassword,
    @JsonProperty("new_password")
        @NotBlank
        @Size(min = 12, max = 256, message = "new_password must be at least 12 characters")
        String newPassword) {}
