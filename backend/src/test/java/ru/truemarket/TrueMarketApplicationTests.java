package ru.truemarket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Phase 0 smoke-тест.
 *
 * <p>Полноценный {@code @SpringBootTest} запустится с Testcontainers (Postgres + Redis + RabbitMQ)
 * — это будет в Phase 1, когда появятся первые бизнес-классы. На Phase 0 проверяем только то, что
 * классы загружаются и Spring Modulith видит все объявленные модули.
 *
 * <p>{@code common} — discovered-модуль Spring Modulith (пакет под базовым {@code ru.truemarket}
 * без {@code @ApplicationModule}). Он перечислен в карте модулей ADR-006 и проходит {@code
 * ModularityTest.verify()} — поэтому входит в ожидаемый список (TASK-100).
 */
class TrueMarketApplicationTests {

  @Test
  void modulesAreDiscovered() {
    var modules = ApplicationModules.of(TrueMarketApplication.class);
    var moduleNames = modules.stream().map(m -> m.getName()).toList();

    assertThat(moduleNames)
        .containsExactlyInAnyOrder(
            "auth",
            "catalog",
            "orders",
            "payments",
            "delivery",
            "verification",
            "reviews",
            "analytics",
            "notifications",
            "admin",
            "common");
  }
}
