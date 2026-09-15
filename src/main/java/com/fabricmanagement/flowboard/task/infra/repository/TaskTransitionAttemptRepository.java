package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskTransitionAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTransitionAttemptRepository
    extends JpaRepository<TaskTransitionAttempt, UUID> {
  Optional<TaskTransitionAttempt> findByTenantIdAndActorIdAndIdempotencyKey(
      UUID tenantId, UUID actorId, String idempotencyKey);
}
