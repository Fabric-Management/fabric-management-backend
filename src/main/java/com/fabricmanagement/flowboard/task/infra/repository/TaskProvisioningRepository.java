package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.domain.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Native insert boundary for the active Task generation-key constraint. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class TaskProvisioningRepository {

  private static final String INSERT_SQL =
      """
      INSERT INTO flowboard.task (
          id, tenant_id, uid, task_number, board_id, title, description, task_type,
          module_type, priority, priority_score, deadline, estimated_hours, actual_hours,
          status, entity_type, entity_id, source_type, source_id, generation_key,
          workflow_definition_id, workflow_version, is_deadline_warning_fired,
          is_active, created_at, created_by, updated_at, updated_by, version
      ) VALUES (
          :id, :tenantId, :uid, :taskNumber, :boardId, :title, :description, :taskType,
          :moduleType, :priority, :priorityScore, :deadline, :estimatedHours, 0,
          'BACKLOG', :entityType, :entityId, :sourceType, :sourceId, :generationKey,
          :workflowDefinitionId, :workflowVersion, FALSE,
          TRUE, :createdAt, :actorId, :createdAt, :actorId, 0
      )
      ON CONFLICT (tenant_id, generation_key)
          WHERE is_active = TRUE AND closed_at IS NULL
      DO NOTHING
      RETURNING id
      """;

  @PersistenceContext private EntityManager entityManager;
  private final Clock clock;
  private final AuditorAware<UUID> auditorProvider;

  public boolean insert(Task task, UUID tenantId) {
    bindTenant(tenantId);
    UUID actorId =
        auditorProvider
            .getCurrentAuditor()
            .orElseThrow(() -> new IllegalStateException("Task provisioning requires an auditor"));
    Instant createdAt = Instant.now(clock);
    task.prepareNativeInsert(tenantId, actorId, createdAt);
    var query =
        entityManager
            .createNativeQuery(INSERT_SQL)
            .setParameter("id", task.getId())
            .setParameter("tenantId", tenantId)
            .setParameter("uid", task.getUid())
            .setParameter("taskNumber", task.getTaskNumber())
            .setParameter("boardId", task.getBoardId())
            .setParameter("title", task.getTitle())
            .setParameter("description", task.getDescription())
            .setParameter("taskType", task.getTaskType().name())
            .setParameter("moduleType", task.getModuleType().name())
            .setParameter("priority", task.getPriority().name())
            .setParameter("priorityScore", task.getPriorityScore())
            .setParameter(
                "deadline", task.getDeadline() == null ? null : Date.valueOf(task.getDeadline()))
            .setParameter("estimatedHours", task.getEstimatedHours())
            .setParameter("entityType", task.getEntityType())
            .setParameter("entityId", task.getEntityId())
            .setParameter("sourceType", task.getSourceType())
            .setParameter("sourceId", task.getSourceId())
            .setParameter("generationKey", task.getGenerationKey())
            .setParameter("workflowDefinitionId", task.getWorkflowDefinitionId())
            .setParameter("workflowVersion", task.getWorkflowVersion())
            .setParameter("createdAt", Timestamp.from(createdAt))
            .setParameter("actorId", actorId);
    return !query.getResultList().isEmpty();
  }

  private void bindTenant(UUID tenantId) {
    if (!TenantContext.requireTenantId().equals(tenantId)) {
      throw new IllegalStateException("Task provisioning tenant does not match the current tenant");
    }
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenant', :tenantId, true)")
        .setParameter("tenantId", tenantId.toString())
        .getSingleResult();
  }
}
