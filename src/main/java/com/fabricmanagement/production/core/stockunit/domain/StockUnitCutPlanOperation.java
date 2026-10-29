package com.fabricmanagement.production.core.stockunit.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable receipt; replay returns the original evidence and child identities. */
@Entity
@Table(name = "stock_unit_cut_plan_operation", schema = "production")
@Getter
@NoArgsConstructor
public class StockUnitCutPlanOperation extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID operationId;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private UUID targetLineId;

  @Column(nullable = false, updatable = false)
  private String requestHash;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String resultJson;

  public static StockUnitCutPlanOperation record(
      UUID operation, UUID actor, UUID line, String hash, String resultJson) {
    var row = new StockUnitCutPlanOperation();
    row.operationId = operation;
    row.actorId = actor;
    row.targetLineId = line;
    row.requestHash = hash;
    row.resultJson = resultJson;
    return row;
  }

  @Override
  protected String getModuleCode() {
    return "CUTOP";
  }
}
