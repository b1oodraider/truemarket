/**
 * Модуль доставки: трекинг, уровни продавца, география.
 *
 * <p>Зависимости (через api/): orders.
 *
 * <p>Трекинг через прямые API российских курьеров (ADR-005): СДЭК, Почта России, Boxberry, Яндекс
 * Доставка. Каждый перевозчик — реализация {@code CarrierTrackingClient} в подпакете {@code
 * tracking.<carrier>}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Delivery",
    allowedDependencies = {"orders"})
package ru.truemarket.delivery;
