# ADR-009 — Шифрование чувствительных полей в БД

- **Статус:** Accepted
- **Дата:** 2026-05-07
- **Автор:** Tech Lead

## Контекст

В ERD (`docs/erd.dbml`) есть поля, защита которых обязательна по 152-ФЗ и здравому смыслу:

| Таблица.Поле | Что | Уровень риска |
|---|---|---|
| `verification.sellers.bank_account` | Номер расчётного счёта продавца | **Высокий** — финансовый ущерб при утечке |
| `verification.sellers.inn` для физлиц-ИП | ИНН в формате 12 цифр содержит дату рождения | Средний — частично ПДн |
| `verification.verification_documents.s3_key` | Само хранилище в S3 (паспорта, выписки ЕГРЮЛ) | Высокий, но это S3-уровень — отдельный ADR |
| Будущие: `customer_payment_tokens` | Токены карт от ЮKassa — мы не храним PAN | Низкий (токены, не PAN) |

В исходном ERD написано «encrypted at rest» рядом с `bank_account`, но **механизма не определено**. Без явного ADR разработчик в Phase 1 будет вынужден изобретать на ходу — обычно это `pgcrypto.pgp_sym_encrypt` с ключом в env, что плохо масштабируется и не ротируется.

### Что НЕ покрывает этот ADR
- Шифрование S3-объектов (паспорта, документы) — отдельный ADR в Phase 1 (server-side encryption Yandex Object Storage + KMS).
- TLS-шифрование транспорта — это HTTPS, уже зафиксировано в CLAUDE.md §13.2.
- Шифрование БД-уровня (TDE / Transparent Data Encryption) — на старте не используем, полагаемся на шифрование диска у облачного провайдера.

## Решение

**App-level AES-GCM-256 шифрование с envelope encryption и наименованием колонок суффиксом `_enc`.**

Конкретно:

1. **Шифр:** AES-256-GCM (authenticated encryption, защита от bit-flipping).
2. **Источник ключей:** envelope encryption.
   - **DEK (Data Encryption Key)** — 256-битный AES-ключ, генерируется per-record (опционально) или per-table (на старте — per-table).
   - **KEK (Key Encryption Key)** — master-ключ, хранится в **HashiCorp Vault** на проде, в env-vars (dev/test).
   - DEK шифруется KEK'ом и хранится рядом с данными (envelope-pattern).
3. **Библиотека:** **Google Tink** (`com.google.crypto.tink:tink:1.15.0`).
   - Опытная команда Google, активно поддерживается, security-first дизайн.
   - Предотвращает типичные ошибки (повтор nonce, mismatching tags, padding oracles).
   - Native поддержка envelope-encryption через `KmsClient`.
4. **Naming convention:** все шифрованные колонки имеют суффикс `_enc` (`bank_account_enc BYTEA`).
5. **Хранение:** `BYTEA` в PostgreSQL (бинарь, не base64-в-string).
6. **JPA-интеграция:** через `@Converter` (AttributeConverter) — для приложения поле выглядит как обычный `String`, но при записи/чтении автоматически шифруется/расшифровывается.

### Список полей, шифруемых на Phase 1

| Таблица.поле | Новое имя | Тип в БД | Кто читает |
|---|---|---|---|
| `verification.sellers.bank_account` | `bank_account_enc` | `BYTEA` | `payments` (для выплат), `admin` (для верификации) |

На Phase 2-3 этот список расширяется новыми ADR (например, если появится KYC-данные документов в БД).

## Обоснование

### Почему app-level, а не pgcrypto

| Критерий | pgcrypto | App-level AES-GCM |
|---|---|---|
| Где ключ | В env-var / БД-функция | В Vault, не в SQL |
| Кто видит plaintext | Любой DBA с правами `SELECT` (после знания ключа) | Только приложение |
| Аудит доступа к ключу | Нет | Vault audit log |
| Ротация ключей | Сложная | Стандартная envelope-операция |
| Производительность | Шифрование на каждой строке в SQL | Шифрование в JVM (можно параллелить) |
| Тестируемость | Зависит от Postgres-расширения | Юнит-тестируется обычным JUnit |
| Запрос по зашифрованному полю | Невозможен (как и при app-level) | Невозможен |

App-level выигрывает там, где важнее (key management, audit, ротация). Pgcrypto проигрывает по самым критичным для compliance критериям.

### Почему Tink, а не «голый» JCA AES-GCM

- JCA требует ручного управления IV/nonce — типичная точка ошибок.
- Tink предотвращает повтор nonce, выбор небезопасных алгоритмов, mismatching aad.
- Tink имеет встроенную поддержку Keysets и rotation.
- Tink покрывает не только симметрию, но и асимметрию (для будущих use-case — подпись webhooks и т. п.).

### Почему envelope encryption

- KEK никогда не покидает Vault. Если злоумышленник получил доступ к БД — DEK у него зашифрован.
- Ротация KEK не требует перешифровывать данные — только перешифровать DEK.
- Стандартный паттерн (используется AWS KMS, Google Cloud KMS, Azure Key Vault).

## Последствия

### Положительные
- Compliance-готовность: 152-ФЗ + банковские требования к хранению BIC/BIK + PCI DSS-friendly паттерн.
- Audit-trail доступа к ключу через Vault.
- Ротация ключей без миграции данных.
- Если БД утечёт (например, через бэкап) — данные бесполезны.

### Отрицательные / риски
- **Невозможен SQL-поиск по `bank_account`** — придётся индексировать хеш отдельной колонкой `bank_account_hash` (HMAC-SHA-256) если понадобится поиск.
- **Сложность тестов** — нужны фиктивные ключи для unit-тестов (Tink поддерживает `KeysetHandle.generateNew()`).
- **Latency overhead** — ~0.1-1 ms на запись/чтение на типовых JVM. Незаметно для UI, но критично для batch-операций.
- **Зависимость от Vault на проде** — если Vault недоступен, приложение не может расшифровать данные. Митигация: Vault HA + локальный cache KEK с коротким TTL (60 сек).

### Что меняется в проекте

1. **pom.xml** добавляется:
   ```xml
   <dependency>
       <groupId>com.google.crypto.tink</groupId>
       <artifactId>tink</artifactId>
       <version>1.15.0</version>
   </dependency>
   ```

2. **Пакет `ru.truemarket.common.crypto`** с:
   - `CryptoService` — публичный интерфейс `encrypt(plaintext) → byte[]`, `decrypt(byte[]) → plaintext`.
   - `TinkCryptoService` — реализация на Tink.
   - `EncryptedStringConverter` — JPA `AttributeConverter<String, byte[]>` для прозрачного использования в `@Entity`.
   - `KeysetProvider` — абстракция поверх источника KEK (Vault, env, тест).

3. **application.yaml** добавляются настройки:
   ```yaml
   truemarket:
     crypto:
       provider: ${CRYPTO_PROVIDER:env}      # env | vault
       env:
         keyset-b64: ${CRYPTO_KEYSET_B64:}   # для dev/test
       vault:
         url: ${VAULT_URL:}
         token: ${VAULT_TOKEN:}
         keyset-path: ${VAULT_KEYSET_PATH:secret/truemarket/crypto/keyset}
   ```

4. **Миграция Flyway** при добавлении первого зашифрованного поля:
   ```sql
   ALTER TABLE verification.sellers ADD COLUMN bank_account_enc BYTEA;
   -- Если bank_account уже есть как plaintext — миграция в два этапа (expand-contract, ADR-007).
   ```

5. **Тесты**: `CryptoServiceTest` с фиксированным in-memory Keyset, integration test через `TestcontainersVault` (опционально).

## Триггеры пересмотра

- Появление требований PCI DSS Level 1 (если будем сами обрабатывать PAN, что НЕ планируется — ЮKassa).
- Регуляторное требование на HSM (Hardware Security Module) — переход на Vault HSM Edition или Yandex KMS.
- Производительность Tink становится bottleneck (маловероятно при объёмах MVP).

## Альтернативы

- **pgcrypto** — отклонён по причинам в таблице выше (key management, audit).
- **`spring-cloud-vault` direct encryption (Transit Backend)** — рассмотрен. Каждое encrypt/decrypt = HTTP-запрос к Vault. Latency 5-50ms vs 0.1-1ms у локального Tink. На горячих путях (выплаты) неприемлемо. Сохранён как fallback для редко-используемых полей.
- **Jasypt** — старая Java-библиотека для encryption. Отклонён: не поддерживает AEAD из коробки, последний релиз 2020.
- **AWS KMS / Yandex KMS прямой** — рассмотрен. Vendor-lock-in, дороже Vault. Vault используется как абстракция (возможна миграция на KMS через Vault Transit Engine).
- **Postgres TDE** — Postgres не имеет нативного TDE. Сторонние решения (EDB) — коммерческие, vendor-lock.

## История

- v1 (2026-05-07): Принят. Tink + envelope encryption + Vault.
