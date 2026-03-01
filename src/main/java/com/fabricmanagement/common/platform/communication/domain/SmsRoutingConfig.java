package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * Defines which delivery channel (WhatsApp, SMS, Email) should be used as the primary and fallback
 * options based on the Tenant's market/country.
 */
@Entity
@Table(
    name = "common_routing_config",
    schema = "common_communication",
    indexes = {@Index(name = "idx_rc_tenant", columnList = "tenant_id")},
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_rc_tenant_country",
          columnNames = {"tenant_id", "country_code"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsRoutingConfig extends BaseEntity {

  /**
   * If null, this is a global default for the given country_code. If set, specific tenant override.
   */
  @Column(name = "tenant_id")
  private UUID tenantId;

  /** Market designation (e.g. TR, GB, US). Null signifies absolute global fallback */
  @Column(name = "country_code", length = 10)
  private String countryCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "primary_channel", nullable = false, length = 30)
  private DeliveryChannel primaryChannel;

  @Enumerated(EnumType.STRING)
  @Column(name = "fallback_channel", length = 30)
  private DeliveryChannel fallbackChannel;

  /** Time in seconds to wait for a success receipt before triggering fallback */
  @Column(name = "timeout_seconds", nullable = false)
  @Builder.Default
  private Integer timeoutSeconds = 20;

  @Override
  protected String getModuleCode() {
    return "ROUTE";
  }
}
