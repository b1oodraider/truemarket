/**
 * Модуль отзывов: написание, модерация, анти-накрутка, верификация покупки.
 *
 * <p>Зависимости (через api/): auth, orders, catalog.
 *
 * <p>Отзыв связан с конкретным {@code Order} — без покупки оставить отзыв нельзя. Анти-накрутка:
 * детектирование аномалий (рейтинг сотен отзывов в день, повторы текстов, фейковые аккаунты).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Reviews",
    allowedDependencies = {"auth", "orders", "catalog"})
package ru.truemarket.reviews;
