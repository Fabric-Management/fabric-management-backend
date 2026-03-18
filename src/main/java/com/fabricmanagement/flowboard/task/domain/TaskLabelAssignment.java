package com.fabricmanagement.flowboard.task.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;

// NOTE [X1] Bu entity bilinçli olarak BaseEntity extend ETMİYOR.
// Gerekçe: Junction table (M:N) — audit trail, soft delete, UID üretimi gereksiz.
// task_label_assignment sadece task_id + label_id çifti tutar, bağımsız yaşam döngüsü yok.
// Eğer ileride label atama geçmişi (kim, ne zaman atadı) istenirse
// BaseEntity'ye geçirilmeli ve created_by/updated_by alanları aktif edilmeli.
/**
 * Task etiket ataması — M:N ilişki tablosu.
 *
 * <p>Bir task birden fazla etiket alabilir. Aynı etiket aynı task'a bir kez atanabilir (unique
 * constraint: task_id + label_id).
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — Bölüm 6.2 TaskLabelAssignment
 */
@Entity
@Table(
    schema = "flowboard",
    name = "task_label_assignment",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "label_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskLabelAssignment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "task_id", nullable = false, updatable = false)
  private UUID taskId;

  @Column(name = "label_id", nullable = false, updatable = false)
  private UUID labelId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }

  public static TaskLabelAssignment create(UUID tenantId, UUID taskId, UUID labelId) {
    var assignment = new TaskLabelAssignment();
    assignment.tenantId = tenantId;
    assignment.taskId = taskId;
    assignment.labelId = labelId;
    return assignment;
  }
}
