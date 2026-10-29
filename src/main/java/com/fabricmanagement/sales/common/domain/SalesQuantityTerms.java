package com.fabricmanagement.sales.common.domain;

import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Current traced quantity decision. Historical rows retain UNRECORDED, never inferred consent. */
@Embeddable
@Getter
@NoArgsConstructor
public class SalesQuantityTerms {
  @Enumerated(EnumType.STRING)
  @Column(name = "quantity_policy", nullable = false, length = 20)
  private SalesQuantityPolicy policy = SalesQuantityPolicy.UNRECORDED;

  @Column(name = "quantity_policy_recorded_by")
  private UUID recordedBy;

  @Column(name = "quantity_policy_recorded_at")
  private Instant recordedAt;

  @Column(name = "quantity_policy_reason", length = 500)
  private String reason;

  public SalesQuantityPolicy getPolicy() {
    return policy == null ? SalesQuantityPolicy.UNRECORDED : policy;
  }

  public void record(SalesQuantityPolicy selected, UUID actor, Instant at, String reason) {
    if (selected == null || selected == getPolicy()) return;
    if (selected == SalesQuantityPolicy.UNRECORDED)
      throw OrderIntakeException.rule(
          "QUANTITY_DECISION_REQUIRED", "A recorded decision cannot be erased into unknown");
    if (actor == null || at == null || reason == null || reason.isBlank() || reason.length() > 500)
      throw OrderIntakeException.rule(
          "QUANTITY_DECISION_REQUIRED", "Record who agreed the quantity policy and why");
    policy = selected;
    recordedBy = actor;
    recordedAt = at;
    this.reason = reason.trim();
  }

  public SalesQuantityTerms copy() {
    var copy = new SalesQuantityTerms();
    copy.policy = getPolicy();
    copy.recordedBy = recordedBy;
    copy.recordedAt = recordedAt;
    copy.reason = reason;
    return copy;
  }
}
