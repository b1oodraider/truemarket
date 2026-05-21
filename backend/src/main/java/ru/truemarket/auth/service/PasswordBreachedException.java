package ru.truemarket.auth.service;

/**
 * Пароль найден в известных утечках (haveibeenpwned) → HTTP 400 (TASK-105).
 *
 * <p>В отличие от 401-ответов это НЕ анти-энумерация: речь о свойстве пароля, а не о существовании
 * аккаунта — клиенту даётся прямая подсказка выбрать другой пароль.
 */
public class PasswordBreachedException extends RuntimeException {

  public PasswordBreachedException() {
    super("password found in known data breaches; choose a different one");
  }
}
