package ru.truemarket.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.truemarket.auth.domain.User;

/**
 * Репозиторий учётных записей. {@code email} — citext, поэтому {@code existsByEmail} сравнивает
 * регистронезависимо на стороне БД (TASK-102).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

  boolean existsByEmail(String email);

  boolean existsByPhone(String phone);

  /** Логин только активных (не soft-deleted, 152-ФЗ) пользователей (TASK-103). */
  Optional<User> findByEmailAndDeletedAtIsNull(String email);

  Optional<User> findByIdAndDeletedAtIsNull(UUID id);
}
