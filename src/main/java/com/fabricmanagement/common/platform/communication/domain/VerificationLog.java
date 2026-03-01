package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * Log and state tracking of an outbound verification/notification message. Acts as the Outbox for
 * the Outbox Pattern.
 */
@Entity
@Table(
    name = "common_verification_log",
    schema = "common_communication",
    indexes = {
      @Index(name = "idx_vl_tenant", columnList = "tenant_id"),
      @Index(name = "idx_vl_status", columnList = "delivery_status"),
      @Index(name = "idx_vl_created_at", columnList = "created_at")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationLog extends BaseEntity {

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** Contact destination (Phone number with code or email) */
  @Column(name = "contact_value", nullable = false, length = 255)
  private String contactValue;

  /** ISO 3166-1 alpha-2 country code (e.g., TR, US, GB) for market-based routing */
  @Column(name = "country_code", length = 10)
  private String countryCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_type", nullable = false, length = 50)
  private VerificationType verificationType;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_channel", nullable = false, length = 30)
  private DeliveryChannel deliveryChannel;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_status", nullable = false, length = 30)
  @Builder.Default
  private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

  /** External Message ID from the provider (e.g., WA WAMID) */
  @Column(name = "external_message_id", length = 255)
  private String externalMessageId;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Override
  protected String getModuleCode() {
    return "VLOG";
  }
}
