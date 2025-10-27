package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Fiber entity.
 */
public interface FiberRepository extends JpaRepository<Fiber, UUID> {

    /**
     * Find fiber by tenant and ID.
     */
    Optional<Fiber> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Find fiber by material ID.
     */
    Optional<Fiber> findByMaterialId(UUID materialId);

    /**
     * Find all active fibers for a tenant.
     */
    List<Fiber> findByTenantIdAndIsActiveTrue(UUID tenantId);

    /**
     * Find fibers by status.
     */
    List<Fiber> findByTenantIdAndStatusAndIsActiveTrue(UUID tenantId, FiberStatus status);

    /**
     * Find fiber by code within tenant.
     */
    boolean existsByTenantIdAndFiberCode(UUID tenantId, String fiberCode);

    /**
     * Find fiber by code.
     */
    Optional<Fiber> findByTenantIdAndFiberCode(UUID tenantId, String fiberCode);

    /**
     * Find active fibers with material details.
     */
    @Query("SELECT f FROM Fiber f " +
           "WHERE f.tenantId = :tenantId AND f.isActive = true " +
           "ORDER BY f.fiberName")
    List<Fiber> findActiveFibersWithDetails(@Param("tenantId") UUID tenantId);
}
