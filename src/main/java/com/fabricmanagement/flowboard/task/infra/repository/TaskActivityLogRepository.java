package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskActivityLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskActivityLogRepository extends JpaRepository<TaskActivityLog, UUID> {

  List<TaskActivityLog> findAllByTaskIdOrderByCreatedAtDesc(UUID taskId);

  // Eskalasyon kalite denetimi için (kaç kez REOPENED oldu vb.)
  long countByTaskIdAndAction(
      UUID taskId, com.fabricmanagement.flowboard.task.domain.TaskAction action);
}
