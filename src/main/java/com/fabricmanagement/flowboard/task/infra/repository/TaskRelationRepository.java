package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskRelation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRelationRepository extends JpaRepository<TaskRelation, UUID> {

  @Query("SELECT r FROM TaskRelation r WHERE r.sourceTaskId = :taskId OR r.targetTaskId = :taskId")
  List<TaskRelation> findByTaskId(@Param("taskId") UUID taskId);

  List<TaskRelation> findBySourceTaskId(UUID sourceTaskId);

  List<TaskRelation> findByTargetTaskId(UUID targetTaskId);
}
