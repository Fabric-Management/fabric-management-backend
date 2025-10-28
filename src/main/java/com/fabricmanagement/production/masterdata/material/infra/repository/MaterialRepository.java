package com.fabricmanagement.production.masterdata.material.infra.repository;

import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Material entity.
 *
 * <p>All queries are tenant-scoped for multi-tenant isolation.</p>
 */
@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    Optional<Material> findByTenantIdAndId(UUID tenantId, UUID id);

    List<Material> findByTenantIdAndIsActiveTrue(UUID tenantId);

    List<Material> findByTenantIdAndMaterialType(UUID tenantId, MaterialType materialType);

    List<Material> findByTenantIdAndMaterialTypeAndIsActiveTrue(UUID tenantId, MaterialType materialType);

    boolean existsByTenantIdAndId(UUID tenantId, UUID id);

    long countByTenantIdAndIsActiveTrue(UUID tenantId);

    long countByTenantIdAndMaterialType(UUID tenantId, MaterialType materialType);
}

