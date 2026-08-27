package com.fabricmanagement.product.yarn.infra.repository;

import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YarnSpinningSystemRepository extends JpaRepository<YarnSpinningSystem, UUID> {

  Optional<YarnSpinningSystem> findByIdAndTenantId(UUID id, UUID tenantId);

  List<YarnSpinningSystem> findByTenantId(UUID tenantId);

  List<YarnSpinningSystem> findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCodeAsc(
      UUID tenantId);

  boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
