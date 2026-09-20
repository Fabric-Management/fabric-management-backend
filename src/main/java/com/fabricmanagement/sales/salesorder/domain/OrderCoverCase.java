package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "sales_ord", name = "order_cover_case")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCoverCase extends BaseEntity {
  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(nullable = false)
  private long revision;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private OrderCoverCaseState state;

  @Column(name = "task_id")
  private UUID taskId;

  @Column(name = "closed_at")
  private Instant closedAt;

  public static OrderCoverCase open(UUID tenantId, UUID salesOrderId) {
    var value = new OrderCoverCase();
    value.setTenantId(tenantId);
    value.salesOrderId = salesOrderId;
    value.revision = 1;
    value.state = OrderCoverCaseState.OPEN;
    return value;
  }

  public void attachTask(UUID taskId) {
    if (this.taskId != null && !this.taskId.equals(taskId)) {
      throw new IllegalStateException("Cover case already belongs to another task");
    }
    this.taskId = taskId;
  }

  public void settle(boolean complete, Instant now) {
    if (state == OrderCoverCaseState.CANCELLED || state == OrderCoverCaseState.SETTLED) {
      throw new IllegalStateException("Cover case is closed");
    }
    revision++;
    state = complete ? OrderCoverCaseState.SETTLED : OrderCoverCaseState.PARTIALLY_SETTLED;
    if (complete && closedAt == null) closedAt = now;
  }

  public void cancel(Instant now) {
    if (state != OrderCoverCaseState.CANCELLED) {
      state = OrderCoverCaseState.CANCELLED;
      revision++;
      if (closedAt == null) closedAt = now;
    }
  }

  @Override
  protected String getModuleCode() {
    return "OCC";
  }
}
