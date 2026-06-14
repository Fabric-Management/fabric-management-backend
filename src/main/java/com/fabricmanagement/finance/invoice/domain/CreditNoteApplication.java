package com.fabricmanagement.finance.invoice.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "finance_credit_note_application",
    schema = "finance",
    indexes = {
      @Index(name = "idx_cna_tenant", columnList = "tenant_id"),
      @Index(name = "idx_cna_credit_note", columnList = "credit_note_id"),
      @Index(name = "idx_cna_target", columnList = "target_invoice_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CreditNoteApplication extends BaseEntity {

  @Column(name = "credit_note_id", nullable = false)
  private UUID creditNoteId;

  @Column(name = "target_invoice_id", nullable = false)
  private UUID targetInvoiceId;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "amount", nullable = false, precision = 19, scale = 4)),
    @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
  })
  private Money amount;

  @Column(name = "applied_at", nullable = false)
  private Instant appliedAt;

  @Override
  protected String getModuleCode() {
    return "CNA";
  }
}
