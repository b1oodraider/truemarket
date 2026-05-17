package ru.truemarket.auth.service;

/**
 * Неверная пара email/password → HTTP 401 (TASK-103). Сообщение НЕ раскрывает, какой именно фактор
 * неверен (anti-enumeration): один и тот же ответ для несуществующего email и неверного пароля.
 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("invalid credentials");
  }
}
