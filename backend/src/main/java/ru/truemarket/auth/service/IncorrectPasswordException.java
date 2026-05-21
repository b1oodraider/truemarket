package ru.truemarket.auth.service;

/**
 * Неверный текущий пароль при смене пароля → HTTP 400 (TASK-106).
 *
 * <p>Не анти-энумерация (пользователь уже аутентифицирован по токену): сообщение прямое.
 */
public class IncorrectPasswordException extends RuntimeException {

  public IncorrectPasswordException() {
    super("current password is incorrect");
  }
}
