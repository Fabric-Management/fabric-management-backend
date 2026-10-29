package com.fabricmanagement.flowboard.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(schema = "flowboard", name = "collection_follow_up_scan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionFollowUpScan extends BaseEntity {
  private Instant completedAt;

  @Column(nullable = false)
  private boolean failed;

  public static CollectionFollowUpScan create() {
    return new CollectionFollowUpScan();
  }

  public void finish(Instant now, boolean failed) {
    this.failed = failed;
    if (!failed) completedAt = now;
  }

  @Override
  protected String getModuleCode() {
    return "CFSCAN";
  }
}
