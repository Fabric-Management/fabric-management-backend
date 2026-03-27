package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Iki gorev arasindaki is mantigi bagimliligini belirtir (orn. biri bitmeden digeri baslayamaz).
 *
 * <p>BaseEntity extend eder: tenantId ile tenant-scoped sorgu yapilabilir, soft delete desteklenir,
 * audit trail (createdAt/createdBy) BaseEntity uzerinden saglanir.
 */
@Entity
@Table(schema = "flowboard", name = "task_dependency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskDependency extends BaseEntity {

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "depends_on_task_id", nullable = false)
  private UUID dependsOnTaskId;

  @Enumerated(EnumType.STRING)
  @Column(name = "dependency_type", nullable = false, length = 20)
  private DependencyType dependencyType;

  @Override
  protected String getModuleCode() {
    return "TDEP";
  }

  public static TaskDependency create(
      UUID tenantId, UUID taskId, UUID dependsOnTaskId, DependencyType dependencyType) {
    var dep = new TaskDependency();
    dep.setTenantId(tenantId);
    dep.taskId = taskId;
    dep.dependsOnTaskId = dependsOnTaskId;
    dep.dependencyType = dependencyType;
    return dep;
  }
}
