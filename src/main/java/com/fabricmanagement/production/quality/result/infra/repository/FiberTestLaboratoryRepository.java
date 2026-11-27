package com.fabricmanagement.production.quality.result.infra.repository;

import com.fabricmanagement.production.quality.result.domain.FiberTestLaboratory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FiberTestLaboratoryRepository extends JpaRepository<FiberTestLaboratory, UUID> {

  Optional<FiberTestLaboratory> findByTenantIdAndTradingPartnerId(UUID tenantId, UUID partnerId);

  boolean existsByTenantIdAndTradingPartnerIdAndIsActiveTrue(UUID tenantId, UUID partnerId);

  @Query(
      """
      SELECT lab.tradingPartnerId FROM FiberTestLaboratory lab
      WHERE lab.tenantId = :tenantId AND lab.isActive = true
      """)
  List<UUID> findActivePartnerIds(@Param("tenantId") UUID tenantId);
}
