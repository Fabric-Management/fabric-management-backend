package com.fabricmanagement.production.execution.stockunit.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Immutable audit log entry for every state change on a {@link StockUnit}.
 *
 * <p>Records are written by the service layer after each operation. They are never soft-deleted
 * ({@code isActive} is always true) and never updated — only appended.
 *
 * <h2>Mandatory Reason Operations</h2>
 *
 * <p>The {@code reason} field is mandatory for:
 *
 * <ul>
 *   <li>GRADE_CHANGE — who changed it and why
 *   <li>REVERSAL — mandatory explanation for undoing a consumption
 *   <li>DISPOSAL — admin must document why the unit is destroyed
 *   <li>QUARANTINE_RELEASE — QC manager must confirm clearance
 *   <li>QC_RELOCATE — custodian must document the physical move
 * </ul>
 *
 * <p>For other operations (CONSUME, TRANSFER, RESERVE) {@code reason} is optional.
 */
@Entity
@Table(
    name = "stock_unit_audit_log",
    schema = "production",
    indexes = {
      @Index(name = "idx_su_audit_stock_unit_id", columnList = "stock_unit_id"),
      @Index(name = "idx_su_audit_tenant_op", columnList = "tenant_id, operation_type"),
      @Index(name = "idx_su_audit_actor", columnList = "actor_id")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StockUnitAuditLog extends BaseEntity {

  /** The StockUnit this log entry belongs to. */
  @Column(name = "stock_unit_id", nullable = false, updatable = false)
  private UUID stockUnitId;

  /** The operation that was performed. */
  @Column(name = "operation_type", nullable = false, length = 50, updatable = false)
  private String operationType;

  /** The field that changed (e.g. "status", "currentWeight", "qualityGradeId"). Nullable. */
  @Column(name = "field_name", length = 100, updatable = false)
  private String fieldName;

  /** String representation of the old value. Nullable for creation events. */
  @Column(name = "old_value", length = 500, updatable = false)
  private String oldValue;

  /** String representation of the new value. */
  @Column(name = "new_value", length = 500, updatable = false)
  private String newValue;

  /** The user who performed the operation. */
  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  /** Trust level of the actor at the time of the operation (1–4). */
  @Column(name = "actor_trust_level", nullable = false, updatable = false)
  private int actorTrustLevel;

  /** Mandatory reason text for operations that require justification. Nullable otherwise. */
  @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
  private String reason;

  // ── Known operation type constants ───────────────────────────────────────

  public static final String OP_CREATE = "CREATE";
  public static final String OP_CONSUME = "CONSUME";
  public static final String OP_REVERSAL = "REVERSAL";
  public static final String OP_TRANSFER = "TRANSFER";
  public static final String OP_QC_RELOCATE = "QC_RELOCATE";
  public static final String OP_GRADE_CHANGE = "GRADE_CHANGE";
  public static final String OP_HOLD = "HOLD";
  public static final String OP_HOLD_RELEASE = "HOLD_RELEASE";
  public static final String OP_QUARANTINE = "QUARANTINE";
  public static final String OP_QUARANTINE_RELEASE = "QUARANTINE_RELEASE";
  public static final String OP_RESERVE = "RESERVE";
  public static final String OP_RESERVE_RELEASE = "RESERVE_RELEASE";
  public static final String OP_DISPOSE = "DISPOSE";
  public static final String OP_FLAG = "FLAG";
  public static final String OP_FLAG_CLEAR = "FLAG_CLEAR";

  // ── Factory Methods ───────────────────────────────────────────────────────

  /**
   * Creates an audit log entry for a StockUnit operation.
   *
   * @param tenantId tenant context
   * @param stockUnitId the affected stock unit
   * @param operationType one of the OP_* constants
   * @param fieldName the field that changed (null for full-entity operations)
   * @param oldValue string representation of old value
   * @param newValue string representation of new value
   * @param actorId the user performing the operation
   * @param actorTrustLevel trust level of the actor (1–4)
   * @param reason mandatory justification for some operations
   */
  public static StockUnitAuditLog of(
      UUID tenantId,
      UUID stockUnitId,
      String operationType,
      String fieldName,
      String oldValue,
      String newValue,
      UUID actorId,
      int actorTrustLevel,
      String reason) {

    StockUnitAuditLog log =
        StockUnitAuditLog.builder()
            .stockUnitId(stockUnitId)
            .operationType(operationType)
            .fieldName(fieldName)
            .oldValue(oldValue)
            .newValue(newValue)
            .actorId(actorId)
            .actorTrustLevel(actorTrustLevel)
            .reason(reason)
            .build();

    log.setTenantId(tenantId);
    log.onCreate();
    return log;
  }

  @Override
  protected String getModuleCode() {
    return "SU-LOG";
  }
}
