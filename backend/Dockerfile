# ==============================================================================
# Multi-stage Dockerfile для TrueMarket backend.
# Phase 0 — рабочий образ, можно дорабатывать в Phase 5 (нативный AOT, distroless).
# ==============================================================================

# ===== Stage 1: build =====
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Кэшируем зависимости отдельным слоем
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline

# Копируем исходники и собираем
COPY src src
RUN ./mvnw -B -ntp -DskipTests package && \
    mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/truemarket-backend.jar extract --destination target/extracted

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre-alpine
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
