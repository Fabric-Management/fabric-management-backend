package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "sales_ord", name = "order_cover_case_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCoverCaseLine extends BaseEntity {
  @Column(name = "case_id", nullable = false, updatable = false)
  private UUID caseId;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Column(name = "settled_at")
  private Instant settledAt;

  @Column(name = "result_id")
  private UUID resultId;

  public static OrderCoverCaseLine unresolved(UUID tenantId, UUID caseId, UUID lineId) {
    var value = new OrderCoverCaseLine();
    value.setTenantId(tenantId);
    value.caseId = caseId;
    value.salesOrderLineId = lineId;
    return value;
  }

  public boolean unresolved() {
    return settledAt == null;
  }

  public void settle(UUID resultId, Instant now) {
    if (!unresolved()) throw new IllegalStateException("Cover line is already settled");
    this.resultId = resultId;
    this.settledAt = now;
  }

  @Override
  protected String getModuleCode() {
    return "OCL";
  }
}
