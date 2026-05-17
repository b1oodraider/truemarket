/**
 * Модуль каталога: товары, категории, атрибуты, поиск, маркировка «Честный ЗНАК».
 *
 * <p>Зависимости (через api/): auth.
 *
 * <p>Поисковый движок — Meilisearch (ADR-002), за интерфейсом {@code ProductSearchEngine}.
 * Маркировка реализуется в подпакете {@code marking} (см. CLAUDE.md 5.3.2).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Catalog",
    allowedDependencies = {"auth"})
package ru.truemarket.catalog;
