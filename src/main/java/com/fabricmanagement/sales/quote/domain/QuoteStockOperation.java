package com.fabricmanagement.sales.quote.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.quote.dto.QuoteRollSelectionDto;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/** Immutable command receipt; no stock ownership is stored here. */
@Entity
@Table(name = "quote_stock_operation", schema = "sales")
@Getter
@NoArgsConstructor
public class QuoteStockOperation extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID quoteId;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private UUID operationId;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestHash;

  @Type(JsonType.class)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private QuoteRollSelectionDto result;

  public static QuoteStockOperation record(
      UUID quote, UUID actor, UUID operation, String hash, QuoteRollSelectionDto result) {
    var value = new QuoteStockOperation();
    value.quoteId = quote;
    value.actorId = actor;
    value.operationId = operation;
    value.requestHash = hash;
    value.result = result;
    return value;
  }

  @Override
  public String getModuleCode() {
    return "QSOP";
  }
}
