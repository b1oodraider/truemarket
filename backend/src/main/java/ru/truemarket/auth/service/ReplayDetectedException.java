package ru.truemarket.auth.service;

/**
 * Повторное использование уже ротированного/отозванного refresh-токена — сигнал кражи (TASK-104).
 * Вся цепочка токенов пользователя отзывается; ответ — 401 (как обычный invalid, anti-enumeration).
 */
public class ReplayDetectedException extends RuntimeException {

  public ReplayDetectedException() {
    super("refresh token replay detected");
  }
}
