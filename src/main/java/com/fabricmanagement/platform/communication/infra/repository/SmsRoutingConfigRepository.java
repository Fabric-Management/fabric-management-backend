package com.fabricmanagement.platform.communication.infra.repository;

import com.fabricmanagement.platform.communication.domain.SmsRoutingConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsRoutingConfigRepository extends JpaRepository<SmsRoutingConfig, UUID> {

  /** Find tenant-specific override config */
  Optional<SmsRoutingConfig> findByTenantIdAndCountryCodeAndIsActiveTrue(
      UUID tenantId, String countryCode);

  /** Find global default config for the country */
  Optional<SmsRoutingConfig> findByTenantIdIsNullAndCountryCodeAndIsActiveTrue(String countryCode);
}
