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
 * Görevler arası ilişki (Örn. duplicate, related) — bloklayan dependency yerine sadece bağlantı
 * belirtmek için kullanılır (Mondays Connect Boards).
 */
@Entity
@Table(schema = "flowboard", name = "task_relation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskRelation extends BaseEntity {

  @Column(name = "source_task_id", nullable = false)
  private UUID sourceTaskId;

  @Column(name = "target_task_id", nullable = false)
  private UUID targetTaskId;

  @Enumerated(EnumType.STRING)
  @Column(name = "relation_type", nullable = false, length = 30)
  private RelationType relationType;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(length = 255)
  private String note;

  public TaskRelation(
      UUID tenantId,
      UUID sourceTaskId,
      UUID targetTaskId,
      RelationType relationType,
      UUID createdByUserId,
      String note) {
    this.setTenantId(tenantId);
    this.sourceTaskId = sourceTaskId;
    this.targetTaskId = targetTaskId;
    this.relationType = relationType;
    this.createdByUserId = createdByUserId;
    this.note = note;
  }

  @Override
  protected String getModuleCode() {
    return "TSK";
  }
}
