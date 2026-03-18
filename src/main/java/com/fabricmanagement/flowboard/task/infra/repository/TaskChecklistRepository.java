package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskChecklist;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskChecklistRepository extends JpaRepository<TaskChecklist, UUID> {

  List<TaskChecklist> findAllByTaskIdOrderByDisplayOrderAsc(UUID taskId);

  @Modifying
  @Query("DELETE FROM TaskChecklist t WHERE t.taskId = :taskId")
  void deleteByTaskId(@Param("taskId") UUID taskId);
}
