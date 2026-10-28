package com.fabricmanagement.production.core.stockunit.infra.repository;

import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockUnitCutSessionRepository extends JpaRepository<StockUnitCutSession, UUID> {
  Optional<StockUnitCutSession> findByTenantIdAndIdAndActorId(UUID tenant, UUID id, UUID actor);

  Optional<StockUnitCutSession> findByTenantIdAndSessionId(UUID tenant, UUID session);

  java.util.List<StockUnitCutSession> findByTenantIdAndTargetLineIdAndEndedFalse(
      UUID tenant, UUID line);

  java.util.List<StockUnitCutSession> findByTenantIdAndTargetTypeAndTargetIdAndEndedFalse(
      UUID tenant, String type, UUID document);

  java.util.List<StockUnitCutSession> findByTenantIdAndProductIdAndSessionIdInAndEndedFalse(
      UUID tenant, UUID product, java.util.Collection<UUID> sessions);
}
