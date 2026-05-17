/**
 * Модуль заказов: жизненный цикл, переходы статусов, корзина.
 *
 * <p>Зависимости (через api/): auth, catalog.
 *
 * <p>Все переходы статусов — только через {@code OrderService.transitionStatus(...)}. Прямой UPDATE
 * статуса в БД запрещён (CLAUDE.md 5.5).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Orders",
    allowedDependencies = {"auth", "catalog"})
package ru.truemarket.orders;
