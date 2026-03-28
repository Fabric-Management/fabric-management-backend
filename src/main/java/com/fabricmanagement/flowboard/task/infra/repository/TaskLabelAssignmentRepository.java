package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskLabelAssignment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** TaskLabelAssignment repository. */
public interface TaskLabelAssignmentRepository extends JpaRepository<TaskLabelAssignment, UUID> {

  /** Task'ın tüm etiket atamalarını getirir. */
  List<TaskLabelAssignment> findAllByTaskId(UUID taskId);

  /** Birden fazla task için tüm atamalar — list/map işlemlerinde N+1 önlemi. */
  List<TaskLabelAssignment> findAllByTaskIdIn(Collection<UUID> taskIds);

  /** Belirli task + label atamasını bulur. */
  Optional<TaskLabelAssignment> findByTaskIdAndLabelId(UUID taskId, UUID labelId);

  /** Task'taki belirli etiketi siler. */
  void deleteByTaskIdAndLabelId(UUID taskId, UUID labelId);

  /** Task'ın atanmış label ID'lerini içeriyor mu? */
  boolean existsByTaskIdAndLabelId(UUID taskId, UUID labelId);
}
