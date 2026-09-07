package com.fabricmanagement.product.yarn.infra.repository;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class YarnBackfillLockRepository {

  private final EntityManager entityManager;

  public boolean tryAcquire(UUID tenantId) {
    Object acquired =
        entityManager
            .createNativeQuery(
                "SELECT pg_try_advisory_xact_lock("
                    + "hashtext('yarn-backfill:' || CAST(?1 AS text)))")
            .setParameter(1, tenantId)
            .getSingleResult();
    return Boolean.TRUE.equals(acquired);
  }

  public void acquireBlankRemediation(UUID tenantId) {
    entityManager
        .createNativeQuery(
            "SELECT pg_advisory_xact_lock("
                + "hashtext('yarn-blank-remediation:' || CAST(?1 AS text)))")
        .setParameter(1, tenantId)
        .getSingleResult();
  }
}
