package com.fabricmanagement.sales.ownership.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
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
import lombok.Setter;

@Entity
@Table(name = "customer_commercial_assignment", schema = "sales")
@Getter
@Setter(AccessLevel.NONE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerCommercialAssignment extends BaseEntity {

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "representative_id", nullable = false, updatable = false)
  private UUID representativeId;

  @Column(name = "valid_from", nullable = false, updatable = false)
  private Instant validFrom;

  @Column(name = "valid_to")
  private Instant validTo;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, updatable = false, length = 40)
  private AssignmentSource source;

  @Enumerated(EnumType.STRING)
  @Column(name = "decided_by_type", nullable = false, updatable = false, length = 10)
  private AssignmentActorType decidedByType;

  @Column(name = "decided_by_user_id", updatable = false)
  private UUID decidedByUserId;

  @Column(name = "decided_by_system_code", updatable = false, length = 60)
  private String decidedBySystemCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "closed_by_type", length = 10)
  private AssignmentActorType closedByType;

  @Column(name = "closed_by_user_id")
  private UUID closedByUserId;

  @Column(name = "closed_by_system_code", length = 60)
  private String closedBySystemCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "closure_reason", length = 40)
  private AssignmentClosureReason closureReason;

  @Column(name = "policy_version", nullable = false, updatable = false, length = 60)
  private String policyVersion;

  @Column(name = "supersedes_assignment_id", updatable = false)
  private UUID supersedesAssignmentId;

  public static CustomerCommercialAssignment open(
      UUID customerId,
      UUID representativeId,
      Instant validFrom,
      AssignmentSource source,
      ActorRef actor,
      String policyVersion,
      UUID supersedesAssignmentId) {
    CustomerCommercialAssignment assignment = new CustomerCommercialAssignment();
    assignment.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    assignment.representativeId =
        Objects.requireNonNull(representativeId, "representativeId must not be null");
    assignment.validFrom = Objects.requireNonNull(validFrom, "validFrom must not be null");
    assignment.source = Objects.requireNonNull(source, "source must not be null");
    assignment.policyVersion =
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
    assignment.supersedesAssignmentId = supersedesAssignmentId;
    assignment.decidedByType = actor.type();
    assignment.decidedByUserId = actor.userId();
    assignment.decidedBySystemCode = actor.systemCode();
    return assignment;
  }

  public void close(Instant closedAt, AssignmentClosureReason reason, ActorRef actor) {
    if (validTo != null) {
      throw new IllegalStateException(
          "A closed commercial assignment cannot be changed or reopened");
    }
    Instant effectiveClosedAt = Objects.requireNonNull(closedAt, "closedAt must not be null");
    if (effectiveClosedAt.isBefore(validFrom)) {
      throw new IllegalArgumentException("closedAt must not be before validFrom");
    }
    validTo = effectiveClosedAt;
    closureReason = Objects.requireNonNull(reason, "reason must not be null");
    closedByType = actor.type();
    closedByUserId = actor.userId();
    closedBySystemCode = actor.systemCode();
  }

  public boolean isOpen() {
    return validTo == null;
  }

  @Override
  public final void delete() {
    throw new UnsupportedOperationException(
        "Commercial assignments are retained and cannot be deleted");
  }

  @Override
  public final void activate() {
    throw new UnsupportedOperationException(
        "Commercial assignments are immutable after closure and cannot be reopened");
  }

  @Override
  protected String getModuleCode() {
    return "CASS";
  }
}
