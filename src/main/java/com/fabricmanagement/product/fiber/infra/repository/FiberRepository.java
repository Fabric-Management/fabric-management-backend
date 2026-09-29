package com.fabricmanagement.product.fiber.infra.repository;

import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for Fiber entity.
 *
 * <p>Read paths name their tenant scope explicitly: {@code {currentTenant, catalogueOwner}} for
 * reads (FIBER-CATALOG-1), the current tenant only for writes. RLS is the second barrier.
 */
public interface FiberRepository extends JpaRepository<Fiber, UUID> {

  /** Sentinel for "exclude nothing" in {@link #findActiveBlendIdByComposition}. */
  UUID NO_FIBER = new UUID(0L, 0L);

  Optional<Fiber> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Fiber> findByTenantIdInAndId(Collection<UUID> tenantIds, UUID id);

  /** One bulk resolution of composition components, with their shared ISO/category rows. */
  @Query(
      "SELECT f FROM Fiber f LEFT JOIN FETCH f.fiberIsoCode LEFT JOIN FETCH f.fiberCategory "
          + "WHERE f.tenantId IN :tenantIds AND f.id IN :ids")
  List<Fiber> findScopedWithReferences(
      @Param("tenantIds") Collection<UUID> tenantIds, @Param("ids") Collection<UUID> ids);

  @Query(
      "SELECT f FROM Fiber f LEFT JOIN FETCH f.fiberIsoCode LEFT JOIN FETCH f.fiberCategory "
          + "WHERE f.tenantId IN :tenantIds AND f.isActive = true ORDER BY f.fiberName")
  List<Fiber> findActiveInScope(@Param("tenantIds") Collection<UUID> tenantIds);

  @Query(
      "SELECT f FROM Fiber f LEFT JOIN FETCH f.fiberIsoCode LEFT JOIN FETCH f.fiberCategory "
          + "WHERE f.tenantId IN :tenantIds AND f.isActive = true "
          + "AND lower(f.fiberName) LIKE lower(concat('%', :name, '%')) ORDER BY f.fiberName")
  List<Fiber> searchActiveInScope(
      @Param("tenantIds") Collection<UUID> tenantIds, @Param("name") String name);

  @Query("SELECT f FROM Fiber f WHERE f.tenantId IN :tenantIds AND f.product.id = :productId")
  Optional<Fiber> findInScopeByProductId(
      @Param("tenantIds") Collection<UUID> tenantIds, @Param("productId") UUID productId);

  @Query(
      "SELECT f FROM Fiber f LEFT JOIN FETCH f.fiberIsoCode LEFT JOIN FETCH f.fiberCategory "
          + "WHERE f.tenantId IN :tenantIds AND f.product.id IN :productIds")
  List<Fiber> findInScopeByProductIds(
      @Param("tenantIds") Collection<UUID> tenantIds,
      @Param("productIds") Collection<UUID> productIds);

  /**
   * Unscoped product lookup. Not for business decisions: kept for read-model tests that verify
   * shared rows; application code uses {@link #findInScopeByProductId}.
   */
  @Query("SELECT f FROM Fiber f WHERE f.product.id = :productId")
  Optional<Fiber> findByProductId(@Param("productId") UUID productId);

  /** Active rows of exactly one owner (e.g. the shared catalogue in walking-skeleton tests). */
  List<Fiber> findByTenantIdAndIsActiveTrue(UUID tenantId);

  /** Canonical shared pure fibre of an ISO code (the owner publishes pure, undeclared rows). */
  @Query(
      "SELECT f FROM Fiber f JOIN FETCH f.fiberIsoCode i "
          + "WHERE f.tenantId = :ownerId AND upper(i.isoCode) = upper(:isoCode) "
          + "AND f.materialSource IS NULL AND f.isActive = true")
  Optional<Fiber> findCanonicalByIsoCode(
      @Param("ownerId") UUID ownerId, @Param("isoCode") String isoCode);

  boolean existsByTenantIdAndFiberIsoCode_IdAndMaterialSourceAndIsActiveTrue(
      UUID tenantId, UUID fiberIsoCodeId, MaterialSource materialSource);

  Optional<Fiber> findByTenantIdAndFiberIsoCode_IdAndMaterialSourceAndIsActiveTrue(
      UUID tenantId, UUID fiberIsoCodeId, MaterialSource materialSource);

  /**
   * Active tenant blend with exactly this composition (jsonb numeric, order-free equality). Pass
   * {@code NO_FIBER} as {@code excludeId} on create; pass the updated fibre's id on update.
   */
  @Query(
      value =
          "SELECT id FROM production.prod_fiber WHERE tenant_id = :tenantId AND is_active = TRUE "
              + "AND composition = CAST(:compositionJson AS jsonb) "
              + "AND id <> :excludeId LIMIT 1",
      nativeQuery = true)
  Optional<UUID> findActiveBlendIdByComposition(
      @Param("tenantId") UUID tenantId,
      @Param("compositionJson") String compositionJson,
      @Param("excludeId") UUID excludeId);

  /** Transaction-scoped lock serialising create/update of one tenant composition. */
  @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:lockKey))", nativeQuery = true)
  void acquireCompositionLock(@Param("lockKey") String lockKey);
}
