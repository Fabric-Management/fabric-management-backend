package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Kind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Original accepted facts are never overwritten by the rebuildable current projection. */
@Entity
@Table(name = "collection_expectation", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionExpectation extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID obligationId;

  @Column(nullable = false, updatable = false)
  private UUID revisionId;

  @Column(nullable = false, updatable = false)
  private UUID approvalId;

  @Column(nullable = false, updatable = false)
  private Instant agreedAt;

  @Column(nullable = false, updatable = false)
  private Instant activatedAt;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private Kind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private Method method;

  @Column(updatable = false)
  private LocalDate expectedDate;

  @Column(updatable = false)
  private LocalDate chequePaymentDate;

  @Column(nullable = false, updatable = false)
  private boolean awaitsConfirmationDate;

  @Override
  protected String getModuleCode() {
    return "CEXP";
  }

  public static CollectionExpectation record(
      CollectionObligation row, UUID approval, Instant agreed) {
    var value = new CollectionExpectation();
    value.obligationId = row.getId();
    value.revisionId = row.getRevisionId();
    value.approvalId = approval;
    value.agreedAt = agreed;
    value.activatedAt = row.getSourceActivatedAt();
    value.amount = row.getAmount();
    value.kind = row.getKind();
    value.method = row.getMethod();
    value.expectedDate = row.getExpectedDate();
    value.chequePaymentDate = row.getChequePaymentDate();
    value.awaitsConfirmationDate = row.isAwaitsConfirmationDate();
    return value;
  }
}
