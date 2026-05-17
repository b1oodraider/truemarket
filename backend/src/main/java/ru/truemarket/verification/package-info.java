/**
 * Модуль верификации продавцов и товаров.
 *
 * <p>Зависимости (через api/): auth, catalog.
 *
 * <p>Верификация продавца: ИП и ООО на старте (см. OQ-005, решено). Загружаемые документы — выписка
 * ЕГРИП/ЕГРЮЛ, паспорт директора, доверенности. Верификация товара — выборочная + краудсорсинг (см.
 * CLAUDE.md, дифференциатор #2).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Verification",
    allowedDependencies = {"auth", "catalog"})
package ru.truemarket.verification;
