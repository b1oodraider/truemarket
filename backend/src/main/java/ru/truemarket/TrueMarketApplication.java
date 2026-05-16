package ru.truemarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Точка входа модульного монолита TrueMarket.
 *
 * <p>Архитектурные решения:
 *
 * <ul>
 *   <li>ADR-001 — Java 21 + Spring Boot 3
 *   <li>ADR-006 — модульный монолит на Spring Modulith
 * </ul>
 *
 * <p>Каждый модуль — пакет {@code ru.truemarket.<module>} с {@code package-info.java},
 * содержащим аннотацию {@code @ApplicationModule}. Контроль границ обеспечивается
 * архитектурным тестом {@code ModularityTest}.
 */
@SpringBootApplication
@Modulithic(systemName = "TrueMarket")
public class TrueMarketApplication {

  public static void main(String[] args) {
    SpringApplication.run(TrueMarketApplication.class, args);
  }
}
