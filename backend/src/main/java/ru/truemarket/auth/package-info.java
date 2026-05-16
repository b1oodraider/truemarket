/**
 * Модуль аутентификации и авторизации (RBAC: buyer, seller, moderator, admin).
 *
 * <p>Зависимости (через api/): только common.
 *
 * <p>Содержит: JWT-выпуск и валидацию, refresh-tokens с ротацией, проверка паролей через
 * argon2id и haveibeenpwned, политика прав доступа (RBAC).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Auth",
    allowedDependencies = {})
package ru.truemarket.auth;
