package ru.truemarket.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.truemarket.auth.repository.RefreshTokenRepository;

/**
 * Отзыв всей активной цепочки refresh-токенов пользователя в ОТДЕЛЬНОЙ транзакции (TASK-104).
 *
 * <p>{@code REQUIRES_NEW}: при replay-detection отзыв должен закоммититься НЕЗАВИСИМО от того, что
 * вызывающий {@code rotate} затем бросает {@link ReplayDetectedException} (иначе откат внешней
 * транзакции отменил бы и отзыв — токены остались бы валидными). Отдельный bean — чтобы
 * Spring-прокси применил новую транзакцию (self-invocation её бы не дал).
 *
 * <p>Метод {@code public}: proxy-based {@code @Transactional} применяется только к public-методам
 * (publicMethodsOnly), иначе REQUIRES_NEW тихо игнорируется и отзыв откатился бы вместе с rotate.
 */
@Component
class RefreshTokenRevoker {

  private final RefreshTokenRepository tokens;

  RefreshTokenRevoker(RefreshTokenRepository tokens) {
    this.tokens = tokens;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeAllActive(UUID userId) {
    tokens.revokeAllActiveByUser(userId, Instant.now());
  }
}
