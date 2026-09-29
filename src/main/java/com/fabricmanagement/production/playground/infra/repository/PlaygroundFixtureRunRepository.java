package com.fabricmanagement.production.playground.infra.repository;

import com.fabricmanagement.production.playground.domain.PlaygroundFixtureRun;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaygroundFixtureRunRepository extends JpaRepository<PlaygroundFixtureRun, UUID> {

  Optional<PlaygroundFixtureRun> findByTenantId(UUID tenantId);

  /** Serialises concurrent attempts of the same provisioning. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM PlaygroundFixtureRun r WHERE r.tenantId = :tenantId")
  Optional<PlaygroundFixtureRun> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
