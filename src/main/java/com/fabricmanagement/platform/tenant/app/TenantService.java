package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.events.TenantSettingsUpdatedEvent;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.tenant.mapper.TenantRowMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for regular Tenant operations within the current tenant's context.
 *
 * <p>This service operates strictly on the tenant ID resolved from the active session ({@link
 * TenantContext#requireTenantId()}). It exposes NO methods accepting a tenantId parameter,
 * completely preventing cross-tenant access via request-path injection.
 *
 * <p>For cross-tenant administration, onboarding, and background jobs, use {@link
 * TenantSystemService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

  private final DomainEventPublisher eventPublisher;
  private final SystemTransactionExecutor systemExecutor;
  private final ObjectMapper objectMapper;

  /**
   * Get the current tenant's details.
   *
   * @return TenantDto for the active context
   */
  public TenantDto getMyTenant() {
    UUID tenantId = TenantContext.requireTenantId();
    return systemExecutor
        .executeQuery(
            "SELECT * FROM common_tenant.common_tenant WHERE id = ?",
            TenantRowMapper.INSTANCE,
            tenantId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Current tenant not found: " + tenantId));
  }

  /**
   * Get settings for the current tenant.
   *
   * @return TenantSettings (never null, returns defaults if missing)
   */
  public TenantSettings getMySettings() {
    UUID tenantId = TenantContext.requireTenantId();
    try {
      String settingsJson =
          systemExecutor.executeQueryForObject(
              "SELECT settings FROM common_tenant.common_tenant WHERE id = ?",
              (rs, i) -> rs.getString("settings"),
              tenantId);
      if (settingsJson == null || settingsJson.isBlank()) {
        return TenantSettings.defaults();
      }
      return objectMapper.readValue(settingsJson, TenantSettings.class);
    } catch (Exception e) {
      log.error(
          "Unexpected error loading settings for current tenant {}: {}",
          tenantId,
          e.getMessage(),
          e);
      return TenantSettings.defaults();
    }
  }

  /**
   * Update settings for the current tenant.
   *
   * @param settings New settings to apply
   * @return Updated TenantSettings
   */
  public TenantSettings updateMySettings(TenantSettings settings) {
    UUID tenantId = TenantContext.requireTenantId();
    String settingsJson;
    try {
      settingsJson = objectMapper.writeValueAsString(settings);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to serialize tenant settings", e);
    }

    int updated =
        systemExecutor.executeInTransaction(
            jdbc ->
                jdbc.update(
                    "UPDATE common_tenant.common_tenant SET settings = ?::jsonb, updated_at = now(), version = version + 1 WHERE id = ?",
                    settingsJson,
                    tenantId));

    if (updated == 0) {
      throw new IllegalStateException("Current tenant not found: " + tenantId);
    }

    eventPublisher.publish(
        new TenantSettingsUpdatedEvent(
            tenantId, settings.getTimezone(), settings.getLocale(), settings.getCurrency()));

    log.info("Tenant settings updated for current tenant: id={}", tenantId);
    return settings;
  }
}
