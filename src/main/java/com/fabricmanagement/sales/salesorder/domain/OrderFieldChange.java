package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Choice;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

/**
 * One applied change of a safe-edit save (CEDIT-02 §7.1), written in the save's transaction: a
 * rolled-back save leaves none and a repeated save adds none. Append-only and kept on its own: the
 * operation id and every value stay here when the save's receipt is cleaned up.
 */
@Entity
@Immutable
@Table(name = "order_field_change", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderFieldChange extends BaseEntity {

  public enum ChangeKind {
    SET,
    CLEAR,
    LINE_ADDED,
    LINE_REMOVED
  }

  /**
   * What a recorded resolution decided: this field, or the whole line the change belongs to (a
   * line-level conflict such as a product change, accepted for every field the save changed there).
   */
  public enum ResolutionScope {
    FIELD,
    LINE
  }

  /** The resolution that decided a change, and its scope. */
  public record Resolution(Choice choice, ResolutionScope scope) {
    public Resolution {
      Objects.requireNonNull(choice, "A resolution has a choice");
      Objects.requireNonNull(scope, "A resolution has a scope");
    }
  }

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "operation_id", nullable = false, updatable = false)
  private UUID operationId;

  /** Logical correlation with the receipt row; no foreign key, the receipt may be cleaned up. */
  @Column(name = "operation_receipt_id", nullable = false, updatable = false)
  private UUID operationReceiptId;

  @Column(name = "line_id", updatable = false)
  private UUID lineId;

  @Column(name = "edit_key", nullable = false, updatable = false, length = 40)
  private String editKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "change_kind", nullable = false, updatable = false, length = 20)
  private ChangeKind changeKind;

  @Type(JsonType.class)
  @Column(name = "old_value", updatable = false, columnDefinition = "jsonb")
  private JsonNode oldValue;

  @Type(JsonType.class)
  @Column(name = "new_value", updatable = false, columnDefinition = "jsonb")
  private JsonNode newValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "resolution", updatable = false, length = 20)
  private Choice resolution;

  @Enumerated(EnumType.STRING)
  @Column(name = "resolution_scope", updatable = false, length = 10)
  private ResolutionScope resolutionScope;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "order_version", nullable = false, updatable = false)
  private long orderVersion;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;

  /** Who changed the order, in which operation, at which resulting version and when. */
  public record Context(
      UUID salesOrderId,
      UUID operationId,
      UUID operationReceiptId,
      UUID actorId,
      long orderVersion,
      Instant changedAt) {
    public Context {
      Objects.requireNonNull(salesOrderId, "Order is required");
      Objects.requireNonNull(operationId, "Operation is required");
      Objects.requireNonNull(operationReceiptId, "Receipt correlation is required");
      Objects.requireNonNull(actorId, "Actor is required");
      Objects.requireNonNull(changedAt, "Change time is required");
    }
  }

  public static OrderFieldChange record(
      Context context,
      UUID lineId,
      String editKey,
      ChangeKind kind,
      JsonNode oldValue,
      JsonNode newValue,
      Resolution resolution) {
    Objects.requireNonNull(editKey, "Edit key is required");
    Objects.requireNonNull(kind, "Change kind is required");
    OrderFieldChange change = new OrderFieldChange();
    change.salesOrderId = context.salesOrderId();
    change.operationId = context.operationId();
    change.operationReceiptId = context.operationReceiptId();
    change.actorId = context.actorId();
    change.orderVersion = context.orderVersion();
    change.changedAt = context.changedAt();
    change.lineId = lineId;
    change.editKey = editKey;
    change.changeKind = kind;
    change.oldValue = oldValue;
    change.newValue = newValue;
    change.resolution = resolution == null ? null : resolution.choice();
    change.resolutionScope = resolution == null ? null : resolution.scope();
    return change;
  }

  @Override
  protected String getModuleCode() {
    return "OFC";
  }
}
