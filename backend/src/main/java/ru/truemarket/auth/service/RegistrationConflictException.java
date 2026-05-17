package ru.truemarket.auth.service;

/** Конфликт при регистрации (email/phone уже заняты) → HTTP 409 (TASK-102). */
public class RegistrationConflictException extends RuntimeException {

  public RegistrationConflictException(String message) {
    super(message);
  }
}
