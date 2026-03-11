package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.BatchOverrideLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link BatchOverrideLog}.
 *
 * <p>Provides queries for audit trail of batch status overrides.
 */
@Repository
public interface BatchOverrideLogRepository extends JpaRepository<BatchOverrideLog, UUID> {

  List<BatchOverrideLog> findByBatchIdOrderByOverriddenAtDesc(UUID batchId);

  List<BatchOverrideLog> findByOverriddenByOrderByOverriddenAtDesc(UUID overriddenBy);

  List<BatchOverrideLog> findByOverriddenAtBetweenOrderByOverriddenAtDesc(Instant from, Instant to);
}
