package com.fabricmanagement.procurement.quote.domain;

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
@Table(name = "supplier_quote_token", schema = "procurement")
@Getter
@Setter
@NoArgsConstructor
public class SupplierQuoteToken extends BaseEntity {

  @Column(name = "rfq_recipient_id", nullable = false)
  private UUID rfqRecipientId;

  @Column(name = "token", nullable = false, unique = true, length = 255)
  private String token;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private SupplierQuoteTokenStatus status = SupplierQuoteTokenStatus.PENDING;

  @Column(name = "used_at")
  private Instant usedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_method", length = 30)
  private QuoteEntryMethod entryMethod;

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
