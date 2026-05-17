package ru.truemarket.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.truemarket.auth.domain.UserConsent;

/** Репозиторий согласий ПДн (152-ФЗ, TASK-102). Append-only по смыслу: записи не удаляются. */
public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {}
