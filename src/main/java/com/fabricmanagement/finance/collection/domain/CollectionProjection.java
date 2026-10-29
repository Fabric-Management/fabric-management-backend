package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collection_projection", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionProjection extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID orderId;

  @Column(nullable = false, updatable = false)
  private UUID planId;

  private UUID revisionId;
  private long sourceVersion;

  /** The order version this was built from. */
  @Column(nullable = false)
  private long sourceOrderVersion;

  private boolean cancelled;
  private boolean stale;
  private boolean sourceMissing;
  private Instant checkedAt;

  /**
   * The last full read of the source; reconciliation reads an order again once this is old. Set by
   * the first {@link #observed}, which happens before a projection is first saved.
   */
  @Column(nullable = false)
  private Instant sourceReadAt;

  /** The last time the source differed from this projection; an old one makes the order cold. */
  @Column(nullable = false)
  private Instant sourceChangedAt;

  @Override
  protected String getModuleCode() {
    return "CPRJ";
  }

  public static CollectionProjection create(UUID order, UUID plan) {
    var value = new CollectionProjection();
    value.orderId = order;
    value.planId = plan;
    return value;
  }

  public void observed(
      UUID revision,
      long version,
      long orderVersion,
      boolean cancelled,
      boolean stale,
      Instant at) {
    boolean moved =
        sourceChangedAt == null
            || sourceMissing
            || !Objects.equals(this.revisionId, revision)
            || this.sourceVersion != version
            || this.sourceOrderVersion != orderVersion
            || this.cancelled != cancelled
            || this.stale != stale;
    this.revisionId = revision;
    this.sourceVersion = version;
    this.sourceOrderVersion = orderVersion;
    this.cancelled = cancelled;
    this.stale = stale;
    this.checkedAt = at;
    this.sourceReadAt = at;
    if (moved) this.sourceChangedAt = at;
    sourceMissing = false;
  }

  public void missing(Instant at) {
    if (!sourceMissing) sourceChangedAt = at;
    sourceMissing = true;
    checkedAt = at;
    sourceReadAt = at;
  }
}
