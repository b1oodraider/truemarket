/**
 * Модуль платежей: эскроу, расчёт комиссий, выплаты продавцам, фискализация (54-ФЗ).
 *
 * <p>Зависимости (через api/): orders.
 *
 * <p>Расчёт комиссий — по правилам ADR-004 (бонус «Быстрая доставка» = уровень Trusted Seller, не
 * складывается). Фискализация — агентская модель через ЮKassa.Чеки (ADR-008). Каждый расчёт
 * сохраняется в immutable {@code commission_log} (ADR-007).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Payments",
    allowedDependencies = {"orders"})
package ru.truemarket.payments;
