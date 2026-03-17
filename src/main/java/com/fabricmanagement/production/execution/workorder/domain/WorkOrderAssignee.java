package com.fabricmanagement.production.execution.workorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prod_work_order_assignee", schema = "production")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkOrderAssignee extends BaseEntity {

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private AssigneeRole role;

  @Column(name = "department_id")
  private UUID departmentId;

  @Column(name = "user_id")
  private UUID userId;

  /**
   * Timestamp of when this assignee was added. Defaults to now at construction time.
   * BaseEntity's @PrePersist hook handles uid/audit fields — no override needed here.
   */
  @Builder.Default
  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt = Instant.now();

  @Override
  protected String getModuleCode() {
    return "WO-ASG";
  }
}
