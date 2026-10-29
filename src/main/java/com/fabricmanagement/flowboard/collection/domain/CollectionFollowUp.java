package com.fabricmanagement.flowboard.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity
@Table(schema = "flowboard", name = "collection_follow_up")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionFollowUp extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID obligationId;

  @Column(nullable = false, updatable = false)
  private int episode;

  private String customerLabel;

  public void label(String name) {
    customerLabel = name;
  }

  private UUID taskId;
  private UUID responsibleUserId;
  private LocalDate nextFollowUpOn;
  private LocalDate promisedOn;

  @Column(precision = 19, scale = 4)
  private BigDecimal promisedAmount;

  private Instant closedAt;
  private String closureReason;
  private String phase;
  private LocalDate dueDate;
  private String currency;

  @Column(precision = 19, scale = 4)
  private BigDecimal outstanding;

  private long obligationVersion;
  private boolean sourceMissing;
  private boolean stale;
  private Instant checkedAt;
  private long activityRevision;

  @Override
  protected String getModuleCode() {
    return "CFU";
  }

  public static CollectionFollowUp open(UUID obligation, int episode) {
    var c = new CollectionFollowUp();
    c.obligationId = obligation;
    c.episode = episode;
    return c;
  }

  public void task(UUID id) {
    taskId = id;
  }

  public void assign(UUID owner) {
    if (!Objects.equals(responsibleUserId, owner)) {
      responsibleUserId = owner;
      activityRevision++;
    }
  }

  /** Whether the case already shows exactly these facts of its obligation. */
  public boolean reflects(
      long version,
      String phase,
      LocalDate due,
      String currency,
      BigDecimal outstanding,
      boolean missing,
      boolean stale) {
    return obligationVersion == version
        && Objects.equals(this.phase, phase)
        && Objects.equals(dueDate, due)
        && Objects.equals(this.currency, currency)
        && (this.outstanding != null
            && outstanding != null
            && this.outstanding.compareTo(outstanding) == 0)
        && sourceMissing == missing
        && this.stale == stale
        && checkedAt != null;
  }

  public void observe(
      long version,
      String phase,
      LocalDate due,
      String currency,
      BigDecimal outstanding,
      boolean missing,
      boolean stale,
      Instant at) {
    if (reflects(version, phase, due, currency, outstanding, missing, stale)) return;
    obligationVersion = version;
    this.phase = phase;
    dueDate = due;
    this.currency = currency;
    this.outstanding = outstanding;
    sourceMissing = missing;
    this.stale = stale;
    checkedAt = at;
  }

  public void contact(LocalDate next, LocalDate promised, BigDecimal amount) {
    if (next != null) nextFollowUpOn = next;
    if (promised != null) {
      promisedOn = promised;
      promisedAmount = amount;
    }
    activityRevision++;
  }

  public void close(Instant now, String reason) {
    if (closedAt == null) {
      closedAt = now;
      closureReason = reason;
      activityRevision++;
    }
  }

  public LocalDate actionableOn() {
    return nextFollowUpOn != null ? nextFollowUpOn : dueDate;
  }
}
