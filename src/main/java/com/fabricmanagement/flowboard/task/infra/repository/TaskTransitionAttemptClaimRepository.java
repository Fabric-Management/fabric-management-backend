package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionAttempt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Claims an idempotency key before any domain side effect executes. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class TaskTransitionAttemptClaimRepository {

  private static final String CLAIM_SQL =
      """
      INSERT INTO flowboard.task_transition_attempt (
          id, tenant_id, uid, task_id, actor_id, idempotency_key, action_key,
          payload_fingerprint, expected_version, outcome,
          is_active, created_at, created_by, updated_at, updated_by, version
      ) VALUES (
          :id, :tenantId, :uid,
          :taskId, :actorId, :idempotencyKey, :actionKey,
          :payloadFingerprint, :expectedVersion, 'PENDING',
          TRUE, :claimedAt, :actorId, :claimedAt, :actorId, 0
      )
      ON CONFLICT (tenant_id, actor_id, idempotency_key) DO NOTHING
      RETURNING id
      """;

  @PersistenceContext private EntityManager entityManager;
  private final Clock clock;
  private final AuditorAware<UUID> auditorProvider;

  public boolean tryClaim(UUID tenantId, TaskActionCommand command) {
    bindTenant(tenantId);
    UUID actorId =
        auditorProvider
            .getCurrentAuditor()
            .orElseThrow(
                () -> new IllegalStateException("Task transition claim requires an auditor"));
    Instant claimedAt = Instant.now(clock);
    TaskTransitionAttempt attempt = TaskTransitionAttempt.claim(command);
    attempt.prepareNativeInsert(tenantId, actorId, claimedAt);
    return !entityManager
        .createNativeQuery(CLAIM_SQL)
        .setParameter("id", attempt.getId())
        .setParameter("tenantId", tenantId)
        .setParameter("uid", attempt.getUid())
        .setParameter("taskId", attempt.getTaskId())
        .setParameter("actorId", attempt.getActorId())
        .setParameter("idempotencyKey", attempt.getIdempotencyKey())
        .setParameter("actionKey", attempt.getActionKey())
        .setParameter("payloadFingerprint", attempt.getPayloadFingerprint())
        .setParameter("expectedVersion", attempt.getExpectedVersion())
        .setParameter("claimedAt", Timestamp.from(claimedAt))
        .getResultList()
        .isEmpty();
  }

  private void bindTenant(UUID tenantId) {
    if (!TenantContext.requireTenantId().equals(tenantId)) {
      throw new IllegalStateException("Task transition tenant does not match the current tenant");
    }
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenant', :tenantId, true)")
        .setParameter("tenantId", tenantId.toString())
        .getSingleResult();
  }
}
