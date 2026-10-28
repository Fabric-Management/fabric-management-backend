package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.State;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_payment_plan_revision", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaymentPlanRevision extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID planId;

  @Column(nullable = false, updatable = false)
  private int revisionNo;

  @Column(nullable = false, updatable = false)
  private long sourceOrderVersion;

  @Column(nullable = false, length = 64, updatable = false)
  private String pricingFingerprint;

  @Column(nullable = false, updatable = false)
  private UUID operationId;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestHash;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(length = 1000, updatable = false)
  private String changeReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private State state;

  private UUID approvalVersionId;
  private UUID agreedApprovalId;
  private Instant agreedAt;
  private Instant activatedAt;
  private java.time.LocalDate activationDate;
  private long resultOrderVersion;

  @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false, updatable = false)
  private java.util.List<String> validationIssues;

  private Instant cancelledAt;

  public static OrderPaymentPlanRevision create(
      UUID planId,
      int number,
      long orderVersion,
      String fingerprint,
      UUID operation,
      String hash,
      UUID actor,
      String reason,
      java.util.List<String> issues) {
    var value = new OrderPaymentPlanRevision();
    value.validationIssues = java.util.List.copyOf(issues);
    value.planId = planId;
    value.revisionNo = number;
    value.sourceOrderVersion = orderVersion;
    value.pricingFingerprint = fingerprint;
    value.operationId = operation;
    value.requestHash = hash;
    value.actorId = actor;
    value.changeReason = reason;
    value.state = State.DRAFT;
    return value;
  }

  public void propose(UUID versionId) {
    if (state != State.DRAFT && state != State.PROPOSED) throw conflict();
    state = State.PROPOSED;
    approvalVersionId = versionId;
  }

  public void agree(UUID versionId, UUID approvalId, Instant now) {
    if (state != State.PROPOSED || !versionId.equals(approvalVersionId)) throw conflict();
    state = State.AGREED;
    agreedApprovalId = approvalId;
    agreedAt = now;
    activatedAt = now;
  }

  public void resultOrderVersion(long value) {
    resultOrderVersion = value;
  }

  public void activationDate(java.time.LocalDate value) {
    activationDate = value;
  }

  public void supersede() {
    if (state == State.AGREED) state = State.SUPERSEDED;
  }

  /**
   * A draft or proposal that a newer revision replaced before it was ever agreed. An agreement is
   * not touched here: it stays in force until the newer revision is agreed.
   */
  public void replaced() {
    if (state == State.DRAFT || state == State.PROPOSED) state = State.SUPERSEDED;
  }

  public void cancel(Instant now) {
    state = State.CANCELLED;
    cancelledAt = now;
  }

  private static OrderDomainException conflict() {
    return OrderDomainException.conflict(
        "PAYMENT_PLAN_REVISION_CLOSED", "This payment plan revision cannot change state");
  }

  @Override
  protected String getModuleCode() {
    return "SPR";
  }
}
