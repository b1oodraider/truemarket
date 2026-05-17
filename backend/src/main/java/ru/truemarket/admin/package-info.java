/**
 * Модуль администратора: модерация контента, управление верификацией, разрешение споров.
 *
 * <p>Зависимости (через api/): auth, verification, orders, reviews.
 *
 * <p>Доступ только для ролей {@code moderator} и {@code admin}. Все действия логируются в audit-log
 * (immutable, см. CLAUDE.md 13.5).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Admin",
    allowedDependencies = {"auth", "verification", "orders", "reviews"})
package ru.truemarket.admin;
