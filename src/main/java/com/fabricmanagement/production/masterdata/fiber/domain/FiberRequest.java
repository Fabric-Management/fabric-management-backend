package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * Tenant-initiated request to add a new fiber to the platform catalog.
 *
 * <p>Tenants submit fiber requests (isoCode, fiberName, fiberType, etc.) for platform review.
 * Platform admins approve or reject; approved requests can be used to create Fiber entities.
 */
@Entity
@Table(
    name = "production_fiber_request",
    schema = "production",
    indexes = {
      @Index(name = "idx_fiber_request_tenant", columnList = "tenant_id"),
      @Index(name = "idx_fiber_request_status", columnList = "status"),
      @Index(name = "idx_fiber_request_requested_by", columnList = "requested_by")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberRequest extends BaseEntity {

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(name = "iso_code", nullable = false, length = 20)
  private String isoCode;

  @Column(name = "fiber_name", nullable = false, length = 255)
  private String fiberName;

  @Column(name = "fiber_type", nullable = false, length = 50)
  private String fiberType;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private FiberRequestStatus status = FiberRequestStatus.PENDING;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "review_note", columnDefinition = "TEXT")
  private String reviewNote;

  @Override
  protected String getModuleCode() {
    return "FREQ";
  }
}
