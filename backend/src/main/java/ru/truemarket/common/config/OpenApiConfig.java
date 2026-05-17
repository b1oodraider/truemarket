package ru.truemarket.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Конфигурация Swagger / OpenAPI 3 для springdoc-openapi.
 *
 * <p>Контракт API — в {@code /docs/api/openapi.yaml} (single source of truth). Генерация из
 * аннотаций — для удобной разработки и тестирования.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI openAPI() {
    var bearerScheme =
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Access token, выпускается /auth/login");

    return new OpenAPI()
        .info(
            new Info()
                .title("TrueMarket API")
                .version("v1")
                .description("Маркетплейс TrueMarket — REST API")
                .license(new License().name("Proprietary")))
        .components(new Components().addSecuritySchemes("bearerAuth", bearerScheme))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
