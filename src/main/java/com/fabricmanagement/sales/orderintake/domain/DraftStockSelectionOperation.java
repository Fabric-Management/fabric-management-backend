package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionDto;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/** Immutable result of an acquisition; a delayed replay cannot resurrect superseded leases. */
@Entity
@Table(name = "draft_stock_selection_operation", schema = "sales_ord")
@Getter
@NoArgsConstructor
public class DraftStockSelectionOperation extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID operationId;

  @Column(nullable = false, updatable = false)
  private UUID selectionId;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestHash;

  @Type(JsonType.class)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private DraftStockSelectionDto result;

  public static DraftStockSelectionOperation record(
      UUID operation, UUID selection, UUID actor, String hash, DraftStockSelectionDto result) {
    var value = new DraftStockSelectionOperation();
    value.operationId = operation;
    value.selectionId = selection;
    value.actorId = actor;
    value.requestHash = hash;
    value.result = result;
    return value;
  }

  @Override
  public String getModuleCode() {
    return "DSOP";
  }
}
