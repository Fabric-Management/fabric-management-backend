package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskAssignee;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** TaskAssignee repository. */
public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, UUID> {

  /** Task'ın aktif atamalarını getirir. */
  List<TaskAssignee> findAllByTaskIdAndIsActiveTrue(UUID taskId);

  /** Task'ın belirli kullanıcıya olan aktif atamasını getirir. */
  Optional<TaskAssignee> findByTaskIdAndUserIdAndIsActiveTrue(UUID taskId, UUID userId);
}
