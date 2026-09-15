package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskAffectedSubject;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAffectedSubjectRepository extends JpaRepository<TaskAffectedSubject, UUID> {
  List<TaskAffectedSubject> findAllByTaskId(UUID taskId);
}
