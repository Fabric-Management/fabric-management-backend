package com.fabricmanagement.common.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.lang.NonNull;

/**
 * JPA Auditing Configuration
 *
 * <p>Provides current auditor (user) for @CreatedBy and @LastModifiedBy fields
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditorAwareConfig {

  @Bean
  public AuditorAware<UUID> auditorProvider() {
    return new AuditorAwareImpl();
  }

  @Slf4j
  static class AuditorAwareImpl implements AuditorAware<UUID> {

    private static final UUID SYSTEM_USER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    @NonNull
    public Optional<UUID> getCurrentAuditor() {
      UUID userId = TenantContext.getCurrentUserId();

      if (userId != null) {
        return Optional.of(userId);
      }

      log.warn(
          "No user context available for JPA auditing — falling back to SYSTEM user. "
              + "This may indicate a missing authentication context on a write operation.");
      return Optional.of(SYSTEM_USER_ID);
    }
  }
}
