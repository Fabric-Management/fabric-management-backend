package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(schema = "finance", name = "collection_reconciliation_health")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionReconciliationHealth extends BaseEntity {
  private Instant completedAt;

  @Column(nullable = false)
  private boolean failed;

  public static CollectionReconciliationHealth create() {
    return new CollectionReconciliationHealth();
  }

  public void finish(Instant now, boolean failed) {
    this.failed = failed;
    if (!failed) completedAt = now;
  }

  @Override
  protected String getModuleCode() {
    return "CRSCAN";
  }
}
