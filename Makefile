# ==============================================================================
# TrueMarket — единый Makefile.
# Все команды из CLAUDE.md раздел 7 живут здесь. Это single source of truth для CI.
# Если команду нельзя выполнить через make — это баг.
# ==============================================================================

SHELL := /bin/bash
.DEFAULT_GOAL := help
.ONESHELL:

# ----- Пути -----
BACKEND_DIR        := backend
INFRA_DIR          := infra
WEB_ADMIN_DIR      := web-admin
MOBILE_BUYER_DIR   := mobile/buyer
MOBILE_SELLER_DIR  := mobile/seller

COMPOSE_DEV  := docker compose -f $(INFRA_DIR)/docker-compose.yml
COMPOSE_TEST := docker compose -f $(INFRA_DIR)/docker-compose.test.yml

MVNW := ./mvnw

# ===== Help ===================================================================
.PHONY: help
help: ## Показать список команд
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-30s\033[0m %s\n", $$1, $$2}'

# ===== Bootstrap ==============================================================
.PHONY: bootstrap
bootstrap: ## Полная подготовка окружения после git clone
	@echo "==> Установка версий инструментов через asdf (если есть)"
	@command -v asdf >/dev/null 2>&1 && asdf install || echo "asdf не найден, пропуск"
	@echo "==> Старт инфраструктуры"
	@$(MAKE) infra-up
	@echo "==> Применение миграций"
	@$(MAKE) db-migrate
	@echo "==> Загрузка тестовых данных"
	@$(MAKE) db-seed
	@echo "==> Готово. Запустите: make backend-run"

# ===== Инфраструктура =========================================================
.PHONY: infra-up infra-down infra-restart infra-logs infra-status infra-clean
infra-up: ## Поднять локальный стек (Postgres + Redis + RabbitMQ + MinIO + Meilisearch)
	$(COMPOSE_DEV) up -d
	@echo "==> Стек запущен. UI: MinIO http://localhost:9001  RabbitMQ http://localhost:15672  Meili http://localhost:7700"

infra-down: ## Остановить локальный стек
	$(COMPOSE_DEV) down

infra-restart: infra-down infra-up ## Перезапустить локальный стек

infra-logs: ## Логи всех сервисов
	$(COMPOSE_DEV) logs -f --tail=100

infra-status: ## Статус сервисов
	$(COMPOSE_DEV) ps

infra-clean: ## Полностью удалить стек И данные (volumes)
	$(COMPOSE_DEV) down -v
	@echo "Все volume'ы удалены"

infra-test-up: ## Поднять тестовый стек (для отладки интеграционных тестов локально)
	$(COMPOSE_TEST) up -d

infra-test-down: ## Остановить тестовый стек
	$(COMPOSE_TEST) down -v

# ===== БД =====================================================================
.PHONY: db-migrate db-migrate-info db-seed db-psql db-reset
# Параметры подключения к локальному Postgres из docker-compose.
# В CI/проде переопределяются через переменные окружения.
DB_URL      ?= jdbc:postgresql://localhost:5432/truemarket
DB_USER     ?= truemarket
DB_PASSWORD ?= truemarket

db-migrate: ## Применить Flyway-миграции (требует поднятого Postgres — make infra-up)
	cd $(BACKEND_DIR) && $(MVNW) flyway:migrate \
		-Dflyway.url=$(DB_URL) -Dflyway.user=$(DB_USER) -Dflyway.password=$(DB_PASSWORD)

db-migrate-info: ## Показать статус миграций
	cd $(BACKEND_DIR) && $(MVNW) flyway:info \
		-Dflyway.url=$(DB_URL) -Dflyway.user=$(DB_USER) -Dflyway.password=$(DB_PASSWORD)

db-seed: ## Засеять тестовые данные (заглушка — реализуется в Phase 1, TASK-005-seed)
	@echo "TODO: db-seed будет реализован в Phase 1. На этом этапе нет данных для seed'а."

db-psql: ## Открыть psql на локальной БД
	$(COMPOSE_DEV) exec postgres psql -U truemarket -d truemarket

db-reset: infra-clean infra-up ## Удалить БД и пересоздать (drop volumes + up + migrate)
	@echo "==> Жду 5 сек чтобы Postgres поднялся..."
	@sleep 5
	$(MAKE) db-migrate

# ===== Backend (Java + Spring Boot, ADR-001) =================================
.PHONY: backend-deps backend-lint backend-format backend-test backend-test-integration \
        backend-build backend-run backend-coverage backend-clean
backend-deps: ## Скачать зависимости Maven
	cd $(BACKEND_DIR) && $(MVNW) -q dependency:resolve

backend-lint: ## Проверить форматирование (Spotless + Google Java Format)
	cd $(BACKEND_DIR) && $(MVNW) -q spotless:check

backend-format: ## Применить автоформатирование
	cd $(BACKEND_DIR) && $(MVNW) -q spotless:apply

backend-test: ## Unit-тесты + архитектурные тесты
	cd $(BACKEND_DIR) && $(MVNW) test

backend-test-integration: ## Интеграционные тесты (Testcontainers, *IT.java — нужен запущенный Docker)
	cd $(BACKEND_DIR) && $(MVNW) verify -P integration

backend-build: ## Сборка JAR
	cd $(BACKEND_DIR) && $(MVNW) -DskipTests package

backend-run: ## Запуск локально с профилем local
	cd $(BACKEND_DIR) && $(MVNW) spring-boot:run -Dspring-boot.run.profiles=local

backend-coverage: ## HTML-отчёт покрытия в target/site/jacoco/index.html
	cd $(BACKEND_DIR) && $(MVNW) verify
	@echo "==> Отчёт: $(BACKEND_DIR)/target/site/jacoco/index.html"

backend-clean: ## Очистить target/
	cd $(BACKEND_DIR) && $(MVNW) clean

# ===== Web admin (React + TypeScript) =========================================
.PHONY: web-deps web-lint web-test web-dev web-build
web-deps: ## pnpm install
	cd $(WEB_ADMIN_DIR) && pnpm install

web-lint: ## ESLint + Prettier check
	cd $(WEB_ADMIN_DIR) && pnpm lint

web-test: ## Vitest
	cd $(WEB_ADMIN_DIR) && pnpm test

web-dev: ## Запуск dev-сервера Vite
	cd $(WEB_ADMIN_DIR) && pnpm dev

web-build: ## Production-сборка
	cd $(WEB_ADMIN_DIR) && pnpm build

# ===== Mobile (Flutter) =======================================================
.PHONY: mobile-buyer-deps mobile-buyer-analyze mobile-buyer-test mobile-buyer-run \
        mobile-buyer-build-android mobile-buyer-build-ios \
        mobile-seller-deps mobile-seller-analyze mobile-seller-test mobile-seller-run \
        mobile-seller-build-android mobile-seller-build-ios
mobile-buyer-deps: ## Flutter pub get для buyer
	cd $(MOBILE_BUYER_DIR) && flutter pub get

mobile-buyer-analyze: ## Flutter analyze для buyer
	cd $(MOBILE_BUYER_DIR) && flutter analyze

mobile-buyer-test: ## Flutter test для buyer
	cd $(MOBILE_BUYER_DIR) && flutter test

mobile-buyer-run: ## Flutter run buyer (debug)
	cd $(MOBILE_BUYER_DIR) && flutter run

mobile-buyer-build-android: ## APK release для buyer
	cd $(MOBILE_BUYER_DIR) && flutter build apk --release

mobile-buyer-build-ios: ## iOS release для buyer (только на macOS)
	cd $(MOBILE_BUYER_DIR) && flutter build ios --release --no-codesign

mobile-seller-deps:           ; cd $(MOBILE_SELLER_DIR) && flutter pub get
mobile-seller-analyze:        ; cd $(MOBILE_SELLER_DIR) && flutter analyze
mobile-seller-test:           ; cd $(MOBILE_SELLER_DIR) && flutter test
mobile-seller-run:            ; cd $(MOBILE_SELLER_DIR) && flutter run
mobile-seller-build-android:  ; cd $(MOBILE_SELLER_DIR) && flutter build apk --release
mobile-seller-build-ios:      ; cd $(MOBILE_SELLER_DIR) && flutter build ios --release --no-codesign

# ===== ADR helpers ============================================================
.PHONY: adr-new adr-check
adr-new: ## Создать новый ADR. Использование: make adr-new title="commission-bonus-stacking"
ifndef title
	@echo "ERROR: укажите title. Пример: make adr-new title=\"choose-database-vendor\""
	@echo "       title должен быть в kebab-case (только латиница, цифры, дефисы)"
	@exit 1
endif
	@last=$$(ls docs/adr/ADR-*.md 2>/dev/null | grep -oE 'ADR-[0-9]+' | sed 's/ADR-//' | sort -n | tail -1); \
	last=$${last:-0}; \
	next=$$(printf "%03d" $$((10#$$last + 1))); \
	file="docs/adr/ADR-$$next-$(title).md"; \
	if [ -f "$$file" ]; then echo "ERROR: уже существует $$file"; exit 1; fi; \
	if [ ! -f "docs/adr/0000-template.md" ]; then echo "ERROR: нет шаблона docs/adr/0000-template.md"; exit 1; fi; \
	sed -e "s|{{NUMBER}}|$$next|g" \
	    -e "s|{{TITLE}}|$(title)|g" \
	    -e "s|{{DATE}}|$$(date +%Y-%m-%d)|g" \
	    docs/adr/0000-template.md > "$$file"; \
	echo "✓ Создан: $$file"; \
	echo "  → не забудьте: 1) переписать заголовок в человеко-читаемом виде"; \
	echo "                 2) удалить HTML-комментарии"; \
	echo "                 3) обновить статус Proposed → Accepted после ревью"

adr-check: ## Проверить все ADR на наличие обязательных секций (то же, что в CI)
	@./scripts/check-adr.sh

# ===== Полный CI-цикл локально ===============================================
.PHONY: ci-local ci-local-full
ci-local: ## Быстрая проверка перед коммитом: lint + unit-тесты + build (без интеграции, без Docker)
	@echo "===== Backend lint ====="
	@$(MAKE) backend-lint
	@echo "===== Backend tests ====="
	@$(MAKE) backend-test
	@echo "===== Backend build ====="
	@$(MAKE) backend-build
	@echo "===== Done ====="

ci-local-full: ## Полный цикл, включая интеграционные тесты (нужен запущенный Docker)
	@command -v docker >/dev/null 2>&1 || { echo "ERROR: Docker не найден — интеграционные тесты не запустятся"; exit 1; }
	@$(MAKE) backend-lint
	@$(MAKE) backend-test
	@$(MAKE) backend-test-integration
	@$(MAKE) backend-build

# ===== Утилиты ================================================================
.PHONY: tree
tree: ## Показать дерево проекта (без node_modules, target, .git)
	@command -v tree >/dev/null 2>&1 && \
		tree -I 'node_modules|target|.git|build|.dart_tool|dist' -L 4 || \
		find . -type d \( -name node_modules -o -name target -o -name .git -o -name build -o -name dist -o -name .dart_tool \) -prune -o -type d -print | head -50
