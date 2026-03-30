package com.fabricmanagement.procurement.rfq.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "supplier_rfq_recipient", schema = "procurement")
@Getter
@Setter
@NoArgsConstructor
public class SupplierRFQRecipient extends BaseEntity {

  @Column(name = "rfq_id", nullable = false, insertable = false, updatable = false)
  private UUID rfqId;

  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  @Column(name = "sent_at")
  private Instant sentAt;

  /**
   * Fix #9 — Default PENDING: alıcı eklendi ama RFQ henüz gönderilmedi. sendRfq() çağrıldığında
   * SENT + sentAt = now() set edilir.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private RfqRecipientStatus status = RfqRecipientStatus.PENDING;

  @Column(name = "response_deadline")
  private Instant responseDeadline;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "PROCUREMENT";
  }
}
