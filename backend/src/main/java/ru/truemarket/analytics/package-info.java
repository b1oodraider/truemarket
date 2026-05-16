/**
 * Модуль аналитики продавца: дашборды, отчёты, метрики продаж.
 *
 * <p>Зависимости (через api/): orders, payments — только read-only.
 *
 * <p>Запросы read-only через материализованные представления; запись отсутствует. Тяжёлые
 * аналитические запросы — через replica connection (см. конфигурацию данных в Фазе 5).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Analytics",
    allowedDependencies = {"orders", "payments"})
package ru.truemarket.analytics;
