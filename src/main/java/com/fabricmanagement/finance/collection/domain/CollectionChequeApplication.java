package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collection_cheque_application", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionChequeApplication extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID chequeId;

  @Column(nullable = false, updatable = false)
  private UUID obligationId;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Override
  protected String getModuleCode() {
    return "CAPL";
  }

  public static CollectionChequeApplication create(
      UUID cheque, UUID obligation, BigDecimal amount) {
    var value = new CollectionChequeApplication();
    value.chequeId = cheque;
    value.obligationId = obligation;
    value.amount = amount;
    return value;
  }
}
