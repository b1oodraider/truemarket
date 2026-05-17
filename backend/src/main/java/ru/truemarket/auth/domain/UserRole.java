package ru.truemarket.auth.domain;

/**
 * Роль пользователя (RBAC, CLAUDE.md §13.1). Соответствует PG-типу {@code user_role} (миграция
 * V202605170001, TASK-101). Имена констант = метки enum в БД (lowercase).
 */
public enum UserRole {
  buyer,
  seller,
  moderator,
  admin
}
