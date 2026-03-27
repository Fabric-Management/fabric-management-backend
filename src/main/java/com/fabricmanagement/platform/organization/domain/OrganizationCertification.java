package com.fabricmanagement.platform.organization.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

/**
 * Organization Certification - Links an organization to a certification with license details.
 *
 * <p>Stores organization-specific certification data: license number, issue/expiry dates, document
 * reference.
 *
 * <p>References {@link FiberCertification} for certification types (GOTS, OEKO-TEX, etc.).
 */
@Entity
@Table(
    name = "organization_certification",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_oc_tenant", columnList = "tenant_id"),
      @Index(name = "idx_oc_organization", columnList = "organization_id"),
      @Index(name = "idx_oc_certification", columnList = "certification_id"),
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationCertification extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "certification_id", nullable = false)
  private FiberCertification certification;

  @Column(name = "license_no", length = 100)
  private String licenseNo;

  @Column(name = "issued_at")
  private LocalDate issuedAt;

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Column(name = "document_ref", length = 255)
  private String documentRef;

  @Override
  protected String getModuleCode() {
    return "OGC";
  }

  /** Check if certification is currently valid (not expired). */
  public boolean isValid() {
    if (validUntil == null) return true;
    return !validUntil.isBefore(LocalDate.now());
  }
}
