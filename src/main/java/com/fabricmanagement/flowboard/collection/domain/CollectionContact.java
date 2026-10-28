package com.fabricmanagement.flowboard.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(schema = "flowboard", name = "collection_contact")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionContact extends BaseEntity {
  public enum Outcome {
    REACHED,
    NO_ANSWER,
    PROMISE_TO_PAY,
    DISPUTED,
    FOLLOW_UP_SCHEDULED
  }

  @Column(nullable = false, updatable = false)
  private UUID followUpId;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private Outcome outcome;

  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(updatable = false)
  private LocalDate promisedOn;

  @Column(updatable = false, precision = 19, scale = 4)
  private BigDecimal promisedAmount;

  @Column(updatable = false)
  private LocalDate nextFollowUpOn;

  @Column(updatable = false, length = 2000)
  private String note;

  @Override
  protected String getModuleCode() {
    return "CCON";
  }

  public static CollectionContact record(
      UUID followUp,
      UUID actor,
      Outcome outcome,
      Instant occurred,
      LocalDate promised,
      BigDecimal amount,
      LocalDate next,
      String note) {
    var c = new CollectionContact();
    c.followUpId = followUp;
    c.actorId = actor;
    c.outcome = outcome;
    c.occurredAt = occurred;
    c.promisedOn = promised;
    c.promisedAmount = amount;
    c.nextFollowUpOn = next;
    c.note = note;
    return c;
  }
}
