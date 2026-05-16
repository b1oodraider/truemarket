/**
 * Модуль уведомлений: push (FCM + APNs), email (SMTP), SMS (опционально).
 *
 * <p>Зависимости (через api/): нет — модуль слушает доменные события через
 * {@code @ApplicationModuleListener}.
 *
 * <p>Каждый канал доставки — реализация {@code NotificationChannel} в подпакете {@code
 * channel.<type>}. Шаблоны хранятся в {@code resources/notifications/templates/}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Notifications",
    allowedDependencies = {})
package ru.truemarket.notifications;
