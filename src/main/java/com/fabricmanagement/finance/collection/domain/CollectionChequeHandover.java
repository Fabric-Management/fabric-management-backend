package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "collection_cheque_handover", schema = "finance")
public class CollectionChequeHandover extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID partnerId;

  @Column(nullable = false, updatable = false, length = 40)
  private String handoverNumber;

  @Column(nullable = false, updatable = false)
  private LocalDate receivedOn;

  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false, length = 1000)
  private String reason;

  @Column(nullable = false, updatable = false, length = 1000)
  private String evidence;

  @Column(nullable = false, updatable = false)
  private int chequeCount;

  public static CollectionChequeHandover record(
      UUID partner,
      String number,
      LocalDate receivedOn,
      Instant occurredAt,
      UUID actor,
      String reason,
      String evidence,
      int chequeCount) {
    var handover = new CollectionChequeHandover();
    handover.partnerId = partner;
    handover.handoverNumber = number;
    handover.receivedOn = receivedOn;
    handover.occurredAt = occurredAt;
    handover.actorId = actor;
    handover.reason = reason;
    handover.evidence = evidence;
    handover.chequeCount = chequeCount;
    return handover;
  }

  @Override
  protected String getModuleCode() {
    return "CHB";
  }
}
