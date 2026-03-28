package com.fabricmanagement.platform.tradingpartner.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

/**
 * Trading Partner Certification - Links a trading partner to a certification with license details.
 *
 * <p>Stores partner-specific certification data: license number, issue/expiry dates, document
 * reference.
 *
 * <p>References {@link FiberCertification} for certification types (GOTS, OEKO-TEX, etc.).
 */
@Entity
@Table(
    name = "partner_trading_partner_certification",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_ptpc_tenant", columnList = "tenant_id"),
      @Index(name = "idx_ptpc_trading_partner", columnList = "trading_partner_id"),
      @Index(name = "idx_ptpc_certification", columnList = "certification_id"),
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingPartnerCertification extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trading_partner_id", nullable = false)
  private TradingPartner tradingPartner;

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
    return "TPC";
  }

  /** Check if certification is currently valid (not expired). */
  public boolean isValid() {
    if (validUntil == null) return true;
    return !validUntil.isBefore(LocalDate.now());
  }
}
