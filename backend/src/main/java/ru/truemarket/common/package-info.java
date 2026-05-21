/**
 * Shared-инфраструктура: web/security (JWT-аутентификация, RFC7807), общие утилиты, value objects.
 *
 * <p><b>Shared OPEN-модуль</b> Spring Modulith ({@code @Modulithic(sharedModules="common")} +
 * {@code type=OPEN}): доступен из всех модулей без перечисления в {@code allowedDependencies}, и
 * его под-пакеты (например {@code common.security}) видимы (OPEN не скрывает internals). Запрещено
 * помещать сюда бизнес-логику или доменные сущности конкретных модулей; только сквозная
 * инфраструктура (TASK-096).
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package ru.truemarket.common;
