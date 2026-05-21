package ru.truemarket.auth.service;

/**
 * Порт проверки пароля по базе утечек (CLAUDE.md §13.1, TASK-105).
 *
 * <p>Интерфейс на стороне потребителя (DIP): {@link RegistrationService} не знает о конкретной
 * интеграции. Реализация (haveibeenpwned) — в {@code auth.external}, изолирована за этим портом,
 * что упрощает будущий split auth в микросервис.
 */
public interface PwnedPasswordChecker {

  /**
   * Встречается ли пароль в известных утечках. Реализация fail-open: при недоступности внешнего
   * сервиса возвращает {@code false} (доступность важнее блокировки на сбое 3rd-party).
   */
  boolean isCompromised(String rawPassword);
}
