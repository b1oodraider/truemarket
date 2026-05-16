package ru.truemarket.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import ru.truemarket.TrueMarketApplication;

/**
 * Архитектурные тесты модульного монолита (ADR-006).
 *
 * <p>Проверяют:
 *
 * <ul>
 *   <li>отсутствие циклических зависимостей между модулями;
 *   <li>соблюдение allowedDependencies в @ApplicationModule;
 *   <li>отсутствие импортов из internal-пакетов чужих модулей.
 * </ul>
 *
 * <p>Параллельно генерирует диаграмму C4 в target/spring-modulith-docs/ — её можно подключить в
 * {@code docs/architecture/}.
 */
class ModularityTest {

  private final ApplicationModules modules = ApplicationModules.of(TrueMarketApplication.class);

  @Test
  void verifyModularStructure() {
    modules.verify();
  }

  @Test
  void writeDocumentationSnapshots() {
    new Documenter(modules)
        .writeModulesAsPlantUml()
        .writeIndividualModulesAsPlantUml();
  }
}
