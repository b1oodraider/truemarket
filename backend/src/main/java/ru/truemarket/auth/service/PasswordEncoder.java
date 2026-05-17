package ru.truemarket.auth.service;

/** Хеширование/проверка пароля. Реализация — argon2id (CLAUDE.md §13.1). */
public interface PasswordEncoder {

  /** Возвращает argon2id-хеш (с солью и параметрами внутри строки). */
  String encode(String rawPassword);

  /** Проверяет сырой пароль против хеша (constant-time внутри argon2). */
  boolean matches(String rawPassword, String encodedHash);
}
