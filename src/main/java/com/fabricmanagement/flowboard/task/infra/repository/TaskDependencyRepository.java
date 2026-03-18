package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskDependency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

  List<TaskDependency> findAllByTaskId(UUID taskId);

  List<TaskDependency> findAllByDependsOnTaskId(UUID dependsOnTaskId);
}
