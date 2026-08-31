package com.fabricmanagement.product.yarn.infra.repository;

import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YarnTestMethodRepository extends JpaRepository<YarnTestMethod, UUID> {

  Optional<YarnTestMethod> findByIdAndTenantId(UUID id, UUID tenantId);

  List<YarnTestMethod> findByTenantId(UUID tenantId);

  List<YarnTestMethod> findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCodeAsc(UUID tenantId);

  Page<YarnTestMethod> findByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

  boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
