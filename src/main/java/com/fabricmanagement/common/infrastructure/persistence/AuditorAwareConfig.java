package com.fabricmanagement.common.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
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

  /** Implementation that gets current user from TenantContext */
  static class AuditorAwareImpl implements AuditorAware<UUID> {

    @Override
    @NonNull
    public Optional<UUID> getCurrentAuditor() {
      UUID userId = TenantContext.getCurrentUserId();

      if (userId != null) {
        return Optional.of(userId);
      }

      // Return system user if no user context
      return Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
  }
}
