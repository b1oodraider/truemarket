package ru.truemarket.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Включает биндинг {@link AuthProperties} (TASK-102). */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {}
