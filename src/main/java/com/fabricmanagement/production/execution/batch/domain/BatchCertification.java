package com.fabricmanagement.production.execution.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.platform.organization.domain.OrganizationCertification;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerCertification;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

/**
 * Batch Certification - Links a batch to a certification with optional references to partner or
 * organization certification records.
 *
 * <p>Stores batch-specific certification data: cert number, validity dates, document URL, and
 * optional links to partner or organization certification records.
 */
@Entity
@Table(
    name = "production_execution_batch_certification",
    schema = "production",
    indexes = {
      @Index(name = "idx_bc_tenant", columnList = "tenant_id"),
      @Index(name = "idx_bc_batch", columnList = "batch_id"),
      @Index(name = "idx_bc_certification", columnList = "certification_id"),
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCertification extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false)
  private Batch batch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "certification_id", nullable = false)
  private FiberCertification certification;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false)
  @Builder.Default
  private BatchCertificationScope scope = BatchCertificationScope.BATCH;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "partner_certification_id")
  private TradingPartnerCertification partnerCertification;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_certification_id")
  private OrganizationCertification orgCertification;

  @Column(name = "cert_number", length = 100)
  private String certNumber;

  @Column(name = "valid_from")
  private LocalDate validFrom;

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Column(name = "certifying_body_ref", length = 255)
  private String certifyingBodyRef;

  @Column(name = "document_url", length = 512)
  private String documentUrl;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  @Enumerated(EnumType.STRING)
  @Column(name = "change_reason", nullable = false, length = 30)
  @Builder.Default
  private BatchCertificationChangeReason changeReason = BatchCertificationChangeReason.INITIAL;

  /**
   * Snapshot at batch completion (DEPLETED). GOTS TC: cert number as of completion time. Set when
   * batch status becomes DEPLETED.
   */
  @Column(name = "cert_number_at_completion", length = 100)
  private String certNumberAtCompletion;

  /**
   * Snapshot at batch completion (DEPLETED). GOTS TC: valid-until as of completion time. Set when
   * batch status becomes DEPLETED.
   */
  @Column(name = "valid_until_at_completion")
  private LocalDate validUntilAtCompletion;

  /**
   * True if this record was initially created from autoFill (partner/facility cert). Set to false
   * when user updates any field. GOTS audit: distinguishes system-filled vs user-edited.
   */
  @Column(name = "is_auto_filled", nullable = false)
  @Builder.Default
  private Boolean isAutoFilled = false;

  @Override
  protected String getModuleCode() {
    return "BC";
  }
}
