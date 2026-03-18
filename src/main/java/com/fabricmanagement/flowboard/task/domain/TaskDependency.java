package com.fabricmanagement.flowboard.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * İki görev arasındaki iş mantığı bağımlılığını belirtir (Örn. Biri bitmeden diğeri başlayamaz).
 *
 * <p>Not: BaseEntity'den miras almaz çünkü tenant isolation Task üzerinden sağlanır ve mantıksal
 * silinme yerine fiziksel silinme tercih edilir.
 */
@Entity
@Table(schema = "flowboard", name = "task_dependency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskDependency {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "depends_on_task_id", nullable = false)
  private UUID dependsOnTaskId;

  @Enumerated(EnumType.STRING)
  @Column(name = "dependency_type", nullable = false, length = 20)
  private DependencyType dependencyType;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  public TaskDependency(UUID taskId, UUID dependsOnTaskId, DependencyType dependencyType) {
    this.taskId = taskId;
    this.dependsOnTaskId = dependsOnTaskId;
    this.dependencyType = dependencyType;
    this.createdAt = OffsetDateTime.now();
  }
}
