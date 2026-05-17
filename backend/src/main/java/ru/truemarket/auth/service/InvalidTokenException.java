package ru.truemarket.auth.service;

/** Невалидный/истёкший/не-refresh токен → HTTP 401 (TASK-103). */
public class InvalidTokenException extends RuntimeException {

  public InvalidTokenException(String message) {
    super(message);
  }
}
