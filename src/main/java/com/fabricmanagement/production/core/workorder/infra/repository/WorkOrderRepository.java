package com.fabricmanagement.production.core.workorder.infra.repository;

import com.fabricmanagement.production.core.workorder.domain.WorkOrder;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderRepository
    extends JpaRepository<WorkOrder, UUID>, JpaSpecificationExecutor<WorkOrder> {
  List<WorkOrder> findByTenantIdAndIsActiveTrue(UUID tenantId);

  @Query(
      value =
          """
          SELECT DISTINCT work_order.output_product_id
          FROM production.prod_work_order work_order
          WHERE work_order.tenant_id = :tenantId
            AND work_order.is_active = TRUE
            AND work_order.status NOT IN ('COMPLETED', 'CANCELLED')
            AND work_order.output_product_id IS NOT NULL
          """,
      nativeQuery = true)
  List<UUID> findOpenReferencedProductIds(@Param("tenantId") UUID tenantId);

  @Query(
      value =
          """
          SELECT count(*)
          FROM production.prod_work_order work_order
          WHERE work_order.tenant_id = :tenantId
            AND work_order.is_active = TRUE
            AND work_order.status NOT IN ('COMPLETED', 'CANCELLED')
            AND work_order.output_product_id IS NULL
            AND work_order.production_specs ->> 'specType' = 'SPINNING'
          """,
      nativeQuery = true)
  long countOpenUnlinkedSpinningWorkOrders(@Param("tenantId") UUID tenantId);

  Optional<WorkOrder> findByWorkOrderNumberAndIsActiveTrue(String workOrderNumber);

  Optional<WorkOrder> findByIdAndTenantIdAndIsActiveTrue(UUID id, UUID tenantId);

  List<WorkOrder> findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID salesOrderLineId);

  boolean existsByTenantIdAndSalesOrderLineIdAndIsActiveTrueAndStatusNotIn(
      UUID tenantId, UUID salesOrderLineId, List<WorkOrderStatus> statuses);

  /**
   * @deprecated Sprint 9: Replaced by JpaSpecificationExecutor with WorkOrderSpecification
   */
  @Deprecated(since = "Sprint 9", forRemoval = true)
  Page<WorkOrder> findByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

  /**
   * @deprecated Sprint 9: Replaced by JpaSpecificationExecutor with WorkOrderSpecification
   */
  @Deprecated(since = "Sprint 9", forRemoval = true)
  Page<WorkOrder> findByTenantIdAndStatusAndIsActiveTrue(
      UUID tenantId, WorkOrderStatus status, Pageable pageable);

  // ── Dashboard Queries ──

  @Query(
      """
      SELECT wo.status AS status, COUNT(wo.id) AS count
      FROM WorkOrder wo
      WHERE wo.tenantId = :tenantId AND wo.isActive = true
      GROUP BY wo.status
      """)
  List<StatusCountProjection> countByStatus(@Param("tenantId") UUID tenantId);

  interface StatusCountProjection {
    WorkOrderStatus getStatus();

    Long getCount();
  }

  /**
   * Aggregate dashboard stats.
   *
   * <p>Key design decisions:
   *
   * <ul>
   *   <li>{@code total_planned_cost}: computed as {@code unit_cost × planned_qty} because the
   *       {@code planned_cost} column is not yet populated by any workflow
   *   <li>{@code deadline IS NOT NULL}: explicit guard for nullable deadline field — PostgreSQL
   *       NULL comparison would filter them anyway, but this is intention-clear
   *   <li>Currency filter: only sums costs where {@code currency} matches, preventing
   *       cross-currency aggregation errors
   * </ul>
   */
  @Query(
      value =
          """
          SELECT
              COUNT(*) FILTER (
                  WHERE deadline IS NOT NULL
                    AND deadline < :now
                    AND status NOT IN ('COMPLETED', 'CANCELLED')
              ) AS overdue_count,

              AVG(yield_percentage) FILTER (
                  WHERE status = 'COMPLETED'
              ) AS avg_yield,

              COUNT(*) FILTER (
                  WHERE status = 'COMPLETED'
              ) AS completed_count,

              COALESCE(SUM(COALESCE(planned_cost, unit_cost * planned_qty)) FILTER (
                  WHERE status <> 'CANCELLED'
                    AND COALESCE(planned_cost_currency, currency) = :currency
              ), 0) AS total_planned_cost,

              COALESCE(SUM(actual_cost) FILTER (
                  WHERE status = 'COMPLETED'
                    AND actual_cost_currency = :currency
              ), 0) AS total_actual_cost

          FROM production.prod_work_order
          WHERE tenant_id = :tenantId AND is_active = true
          """,
      nativeQuery = true)
  DashboardStatsProjection getDashboardStats(
      @Param("tenantId") UUID tenantId,
      @Param("now") Instant now,
      @Param("currency") String currency);

  interface DashboardStatsProjection {
    Long getOverdueCount();

    BigDecimal getAvgYield();

    Long getCompletedCount();

    BigDecimal getTotalPlannedCost();

    BigDecimal getTotalActualCost();
  }
}
