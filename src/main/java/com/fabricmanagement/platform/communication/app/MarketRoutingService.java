package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.platform.communication.domain.DeliveryChannel;
import com.fabricmanagement.platform.communication.domain.SmsRoutingConfig;
import com.fabricmanagement.platform.communication.infra.repository.SmsRoutingConfigRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Service to determine the correct communication channel (Primary/Fallback) based on the Tenant's
 * market (country) settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketRoutingService {

  private final SmsRoutingConfigRepository routingConfigRepository;

  /**
   * Determine the routing configuration for a given tenant and country. If a tenant-specific
   * override exists, it is returned. Otherwise, the global configuration for the country is
   * returned. If no country config exists, a safe default (Email) is returned.
   */
  @Cacheable(value = "routingConfig", key = "#tenantId + '-' + #countryCode")
  public SmsRoutingConfig getRoutingConfig(UUID tenantId, String countryCode) {
    if (countryCode == null || countryCode.isBlank()) {
      return getDefaultSafeConfig();
    }

    // 1. Check for Tenant specific override
    if (tenantId != null) {
      Optional<SmsRoutingConfig> tenantOverride =
          routingConfigRepository.findByTenantIdAndCountryCodeAndIsActiveTrue(
              tenantId, countryCode);
      if (tenantOverride.isPresent()) {
        log.debug(
            "Found tenant-specific routing override for tenant: {}, country: {}",
            tenantId,
            countryCode);
        return tenantOverride.get();
      }
    }

    // 2. Check for Global country configuration (tenantId is null)
    Optional<SmsRoutingConfig> globalConfig =
        routingConfigRepository.findByTenantIdIsNullAndCountryCodeAndIsActiveTrue(countryCode);

    if (globalConfig.isPresent()) {
      log.debug("Found global routing config for country: {}", countryCode);
      return globalConfig.get();
    }

    // 3. Fallback to safe default
    log.warn("No routing config found for country: {}. Falling back to EMAIL.", countryCode);
    return getDefaultSafeConfig();
  }

  /**
   * Safe default configuration if no market rules are defined. Defaults to Email to avoid
   * unexpected SMS/WhatsApp costs.
   */
  private SmsRoutingConfig getDefaultSafeConfig() {
    return SmsRoutingConfig.builder()
        .primaryChannel(DeliveryChannel.EMAIL)
        .fallbackChannel(null)
        .timeoutSeconds(0)
        .build();
  }
}
