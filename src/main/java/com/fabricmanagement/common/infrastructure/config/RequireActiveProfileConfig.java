package com.fabricmanagement.common.infrastructure.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start without a profile.
 *
 * <p>Spring activates the {@code default} profile only when no other is active. Every real way of
 * running the application names one: docker-compose.yml sets {@code docker}, production sets {@code
 * prod}, tests choose {@code test}. Without one the security chain, the seed data and the PII rules
 * would all fall back to settings meant for somewhere else, so the context stops here instead. The
 * check runs as a bean factory post-processor, before any bean is created: no data source is opened
 * and no migration runs.
 */
@Configuration(proxyBeanMethods = false)
@Profile("default")
public class RequireActiveProfileConfig {

  @Bean
  static BeanFactoryPostProcessor requireActiveProfile() {
    return beanFactory -> {
      throw new IllegalStateException(
          "No Spring profile is active. Start the application with docker compose (profile"
              + " docker) or set SPRING_PROFILES_ACTIVE, for example to prod.");
    };
  }
}
