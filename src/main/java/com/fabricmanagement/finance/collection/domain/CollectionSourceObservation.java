package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * Source membership/cancellation observations support later as-of and knowledge-cutoff reporting.
 */
@Entity
@Table(name = "collection_source_observation", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionSourceObservation extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID orderId;

  @Column(nullable = false, updatable = false)
  private UUID planId;

  @Column(updatable = false)
  private UUID revisionId;

  @Column(nullable = false, updatable = false)
  private long sourceVersion;

  @Column(nullable = false, updatable = false)
  private boolean cancelled;

  @Column(nullable = false, updatable = false)
  private boolean stale;

  @Column(nullable = false, updatable = false)
  private boolean sourceMissing;

  @Column(updatable = false)
  private Instant effectiveAt;

  @Column(nullable = false, updatable = false)
  private Instant observedAt;

  @Type(JsonType.class)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<UUID> activeLogicalIds;

  @Override
  protected String getModuleCode() {
    return "CSRC";
  }

  public static CollectionSourceObservation record(
      UUID order,
      UUID plan,
      UUID revision,
      long version,
      boolean cancelled,
      boolean stale,
      boolean missing,
      Instant effective,
      Instant observed,
      List<UUID> ids) {
    var value = new CollectionSourceObservation();
    value.orderId = order;
    value.planId = plan;
    value.revisionId = revision;
    value.sourceVersion = version;
    value.cancelled = cancelled;
    value.stale = stale;
    value.sourceMissing = missing;
    value.effectiveAt = effective;
    value.observedAt = observed;
    value.activeLogicalIds = List.copyOf(ids);
    return value;
  }
}
