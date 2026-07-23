package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the universal {@link Batch} entity.
 *
 * <p>Provides tenant-scoped and product-type-scoped queries for batch lookup and listing. Use
 * derived query methods for filtering by productId, productType, batchCode, or status.
 */
@Repository
public interface BatchRepository
    extends JpaRepository<Batch, UUID>, StockAvailabilityBatchRepository {

  List<Batch> findByTenantId(UUID tenantId);

  List<Batch> findByTenantIdAndIsActiveTrue(UUID tenantId);

  @Lock(LockModeType.OPTIMISTIC)
  Optional<Batch> findByIdAndTenantId(UUID id, UUID tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Batch b WHERE b.id = :id AND b.tenantId = :tenantId")
  Optional<Batch> findByIdAndTenantIdForUpdate(
      @Param("id") UUID id, @Param("tenantId") UUID tenantId);

  List<Batch> findByTenantIdAndProductId(UUID tenantId, UUID productId);

  List<Batch> findByTenantIdAndProductIdAndIsActiveTrue(UUID tenantId, UUID productId);

  List<Batch> findByTenantIdAndIdInAndIsActiveTrue(UUID tenantId, Collection<UUID> ids);

  Optional<Batch> findByTenantIdAndBatchCode(UUID tenantId, String batchCode);

  @Query(
      value =
          """
          SELECT b.id AS batchId,
                 b.batch_code AS batchCode,
                 b.product_id AS productId,
                 p.uid AS productUid,
                 COALESCE(f.fiber_name, p.uid) AS productDisplayName,
                 b.product_type AS productType,
                 c.id AS colorId,
                 c.name AS colorName,
                 b.created_at AS batchCreatedAt,
                 b.status AS status,
                 b.reserved_quantity AS reservedQuantity,
                 b.consumed_quantity AS consumedQuantity
          FROM production.production_execution_batch b
          JOIN production.prod_product p
            ON p.id = b.product_id
          LEFT JOIN production.prod_fiber f
            ON f.product_id = p.id
          LEFT JOIN production.color c
            ON c.id = b.color_id AND c.tenant_id = b.tenant_id
          WHERE b.id = :batchId
            AND b.tenant_id = :tenantId
            AND b.is_active = TRUE
          """,
      nativeQuery = true)
  Optional<QualityBatchSummaryRow> findQualityBatchSummary(
      @Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId);

  boolean existsByTenantIdAndBatchCode(UUID tenantId, String batchCode);

  /**
   * Find the first output batch produced by a specific source (e.g., WorkOrder). Used for cost
   * calculation to determine output product and module type.
   */
  Optional<Batch> findFirstByTenantIdAndSourceIdAndSourceType(
      UUID tenantId, UUID sourceId, BatchSourceType sourceType);

  List<Batch> findByTenantIdAndSourceIdAndSourceTypeAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID sourceId, BatchSourceType sourceType);

  List<Batch> findByTenantIdAndSourceIdAndSourceTypeAndStatusAndIsActiveTrue(
      UUID tenantId, UUID sourceId, BatchSourceType sourceType, BatchStatus status);

  long countByTenantIdAndSourceIdAndSourceTypeAndIsActiveTrue(
      UUID tenantId, UUID sourceId, BatchSourceType sourceType);

  /** Count batches that are direct children of the given parent (for split code generation). */
  long countByTenantIdAndParentBatchId(UUID tenantId, UUID parentBatchId);

  List<Batch> findByTenantIdAndStatus(UUID tenantId, BatchStatus status);

  List<Batch> findByTenantIdAndStatusIn(UUID tenantId, Collection<BatchStatus> statuses);

  @Query(
      """
      SELECT b.id AS batchId,
             c.id AS colorId,
             c.code AS colorCode,
             c.name AS colorName,
             c.colorHex AS colorHex
      FROM Batch b
      JOIN Color c ON c.id = b.colorId AND c.tenantId = b.tenantId
      WHERE b.tenantId = :tenantId
        AND b.id IN :batchIds
      """)
  List<BatchColorProjection> findColorReferencesByBatchIds(
      @Param("tenantId") UUID tenantId, @Param("batchIds") Collection<UUID> batchIds);

  List<Batch> findByTenantIdAndProductIdAndStatusIn(
      UUID tenantId, UUID productId, Collection<BatchStatus> statuses);

  /**
   * Find all batches for a given product (any tenant). Prefer tenant-scoped {@link
   * #findByTenantIdAndProductId(UUID, UUID)} in multi-tenant contexts.
   */
  List<Batch> findAllByProductId(UUID productId);

  /**
   * Find all batches of a given product type (e.g. FIBER, YARN). Prefer tenant-scoped queries when
   * tenant context is available.
   */
  List<Batch> findAllByProductType(ProductType productType);

  /**
   * Find a batch by its unique batch code. In multi-tenant systems prefer {@link
   * #findByTenantIdAndBatchCode(UUID, String)} to scope by tenant.
   */
  Optional<Batch> findByBatchCode(String batchCode);

  /**
   * Find all batches in a given status. Prefer {@link #findByTenantIdAndStatus(UUID, BatchStatus)}
   * when tenant context is available.
   */
  List<Batch> findAllByStatus(BatchStatus status);

  /**
   * Returns true if the given fiber has at least one batch in any of the given statuses.
   *
   * <p>Used by {@code FiberService.deactivateFiber()} to block deactivation when {@code statuses =
   * BatchStatus.PRODUCTION_ACTIVE} — i.e. the fiber still has batches in RESERVED or IN_PROGRESS
   * state on the production floor.
   *
   * <p>This query is index-friendly: {@code (tenant_id, product_id, status)} and returns as soon as
   * one matching row is found.
   */
  boolean existsByTenantIdAndProductIdAndStatusIn(
      UUID tenantId, UUID productId, Collection<BatchStatus> statuses);

  interface BatchColorProjection {
    UUID getBatchId();

    UUID getColorId();

    String getColorCode();

    String getColorName();

    String getColorHex();
  }

  interface QualityBatchSummaryRow {
    UUID getBatchId();

    String getBatchCode();

    UUID getProductId();

    String getProductUid();

    String getProductDisplayName();

    String getProductType();

    UUID getColorId();

    String getColorName();

    java.time.Instant getBatchCreatedAt();

    String getStatus();

    java.math.BigDecimal getReservedQuantity();

    java.math.BigDecimal getConsumedQuantity();
  }
}
