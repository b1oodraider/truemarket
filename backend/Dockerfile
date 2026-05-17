# ==============================================================================
# Multi-stage Dockerfile для TrueMarket backend.
# Phase 0 — рабочий образ; TASK-099 — security-хардинг base-image.
#
# Хардинг (TASK-099):
#   - `apk -U upgrade` в ОБОИХ стейджах: применяет последние security-патчи
#     Alpine-пакетов на момент сборки. Канонический фикс fixable HIGH/CRITICAL
#     CVE, которые Trivy находил в `eclipse-temurin:*-alpine` (Alpine 3.23).
#   - Удаление apk-кэша (`--no-cache` / rm) — без лишнего веса слоёв.
#   - Phase 5 follow-up: пин base-image по immutable digest
#     (`docker buildx imagetools inspect`) + переход на distroline/native AOT.
#     Сейчас тег `21-{jdk,jre}-alpine` отслеживает последний патч upstream;
#     `apk upgrade` гарантирует свежий уровень ОС-патчей детерминированно.
# ==============================================================================

# ===== Stage 1: build =====
FROM eclipse-temurin:21-jdk-alpine AS builder

# Security-патчи ОС-пакетов на момент сборки (TASK-099).
RUN apk -U upgrade --no-cache

WORKDIR /workspace

# Кэшируем зависимости отдельным слоем
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Нормализуем CRLF→LF и ставим +x — образ собирается одинаково независимо от
# хоста: Windows-checkout (core.autocrlf) иначе ломает shebang mvnw (exit 127).
# Defense-in-depth, TASK-099. Корневой фикс (.gitattributes eol=lf) — отдельный
# follow-up по гигиене окончаний строк (см. TASK-099 backlog).
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Копируем исходники и собираем
COPY src src
RUN ./mvnw -B -ntp -DskipTests package && \
    mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/truemarket-backend.jar extract --destination target/extracted

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre-alpine

# Security-патчи ОС-пакетов в runtime-слое (TASK-099).
# Именно этот слой сканирует Trivy — здесь устранение fixable HIGH/CRITICAL.
RUN apk -U upgrade --no-cache

WORKDIR /app

# Непривилегированный пользователь
RUN addgroup -S app && adduser -S -G app -h /app app

# Layered JAR — улучшает использование Docker-кэша при обновлениях
COPY --from=builder /workspace/target/extracted/dependencies/         ./
COPY --from=builder /workspace/target/extracted/spring-boot-loader/   ./
COPY --from=builder /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/extracted/application/           ./

USER app

EXPOSE 8080

# k8s сам делает readiness/liveness probes через /actuator/health/{readiness,liveness}.
# Docker HEALTHCHECK k8s игнорирует, поэтому здесь его нет — добавим только если
# понадобится для docker-compose (Phase 5).

ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
