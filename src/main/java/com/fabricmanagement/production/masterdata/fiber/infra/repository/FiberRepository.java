package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for Fiber entity. */
public interface FiberRepository extends JpaRepository<Fiber, UUID> {

  /** Find fiber by tenant and ID. */
  Optional<Fiber> findByTenantIdAndId(UUID tenantId, UUID id);

  /**
   * Find fiber by material ID.
   *
   * <p>Uses material.id relationship since materialId field was replaced with @ManyToOne Material.
   */
  @Query("SELECT f FROM Fiber f WHERE f.material.id = :materialId")
  Optional<Fiber> findByMaterialId(@Param("materialId") UUID materialId);

  /**
   * Find fibers by multiple material IDs (batch query for performance).
   *
   * <p>Used to optimize Material search when checking Fiber fiberName for multiple materials.
   */
  @Query("SELECT f FROM Fiber f WHERE f.material.id IN :materialIds")
  List<Fiber> findByMaterialIdIn(@Param("materialIds") List<UUID> materialIds);

  /** Find all active fibers for a tenant. */
  List<Fiber> findByTenantIdAndIsActiveTrue(UUID tenantId);

  /**
   * Find all active fibers for the given tenants (e.g. current tenant + system tenant for platform
   * seed). Used so tenant organizations can use platform organization's fiber catalog.
   */
  @Query(
      "SELECT f FROM Fiber f WHERE f.tenantId IN :tenantIds AND f.isActive = true ORDER BY f.fiberName")
  List<Fiber> findByTenantIdInAndIsActiveTrueOrderByFiberName(
      @Param("tenantIds") List<UUID> tenantIds);

  /** Find fibers by name (case-insensitive). */
  List<Fiber> findByTenantIdAndFiberNameContainingIgnoreCase(UUID tenantId, String fiberName);

  /** Find fibers by name across multiple tenants (current + system for platform seed). */
  List<Fiber> findByTenantIdInAndIsActiveTrueAndFiberNameContainingIgnoreCaseOrderByFiberName(
      List<UUID> tenantIds, String fiberName);

  /** Find fibers by status. */
  List<Fiber> findByTenantIdAndStatusAndIsActiveTrue(UUID tenantId, FiberStatus status);

  /** Find active fibers with material details. */
  @Query(
      "SELECT f FROM Fiber f "
          + "WHERE f.tenantId = :tenantId AND f.isActive = true "
          + "ORDER BY f.fiberName")
  List<Fiber> findActiveFibersWithDetails(@Param("tenantId") UUID tenantId);

  /**
   * ✅ Performance: Find fibers by query (filtered search).
   *
   * <p>Used for AI searches to avoid loading all fibers. Searches in fiberName (case-insensitive
   * LIKE).
   *
   * <p>Note: Uses existing findByTenantIdAndFiberNameContainingIgnoreCase method which already
   * filters by query. Limit is applied in Java code.
   */
  // findByTenantIdAndFiberNameContainingIgnoreCase already exists and does filtered search
}
