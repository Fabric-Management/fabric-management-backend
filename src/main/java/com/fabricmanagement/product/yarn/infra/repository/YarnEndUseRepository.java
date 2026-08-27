package com.fabricmanagement.product.yarn.infra.repository;

import com.fabricmanagement.product.yarn.domain.reference.YarnEndUse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YarnEndUseRepository extends JpaRepository<YarnEndUse, UUID> {

  Optional<YarnEndUse> findByIdAndTenantId(UUID id, UUID tenantId);

  List<YarnEndUse> findByTenantId(UUID tenantId);

  List<YarnEndUse> findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCodeAsc(UUID tenantId);

  boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
