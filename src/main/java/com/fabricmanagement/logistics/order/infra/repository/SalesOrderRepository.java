package com.fabricmanagement.logistics.order.infra.repository;

import com.fabricmanagement.logistics.order.domain.OrderStatus;
import com.fabricmanagement.logistics.order.domain.OrderType;
import com.fabricmanagement.logistics.order.domain.SalesOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for SalesOrder entity.
 *
 * <p>All queries are tenant-scoped for data isolation.
 */
public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {

  // ═══════════════════════════════════════════════════════════════════════════
  // Basic Lookups
  // ═══════════════════════════════════════════════════════════════════════════

  Optional<SalesOrder> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<SalesOrder> findByTenantIdAndUid(UUID tenantId, String uid);

  Optional<SalesOrder> findByTenantIdAndOrderNumber(UUID tenantId, String orderNumber);

  // ═══════════════════════════════════════════════════════════════════════════
  // TradingPartner Queries (Faz 1.5)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Find orders by trading partner. */
  List<SalesOrder> findByTenantIdAndTradingPartnerId(UUID tenantId, UUID tradingPartnerId);

  /** Find active orders by trading partner. */
  @Query(
      """
      SELECT o FROM SalesOrder o
      WHERE o.tenantId = :tenantId
      AND o.tradingPartnerId = :partnerId
      AND o.isActive = true
      ORDER BY o.orderDate DESC
      """)
  List<SalesOrder> findActiveByPartner(
      @Param("tenantId") UUID tenantId, @Param("partnerId") UUID partnerId);

  /** Find orders by trading partner with pagination. */
  Page<SalesOrder> findByTenantIdAndTradingPartnerIdAndIsActiveTrue(
      UUID tenantId, UUID tradingPartnerId, Pageable pageable);

  // ═══════════════════════════════════════════════════════════════════════════
  // Status Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<SalesOrder> findByTenantIdAndStatus(UUID tenantId, OrderStatus status);

  List<SalesOrder> findByTenantIdAndStatusIn(UUID tenantId, List<OrderStatus> statuses);

  /** Find open orders (not in terminal status). */
  @Query(
      """
      SELECT o FROM SalesOrder o
      WHERE o.tenantId = :tenantId
      AND o.isActive = true
      AND o.status NOT IN ('DELIVERED', 'CANCELLED')
      ORDER BY o.orderDate DESC
      """)
  List<SalesOrder> findOpenOrders(@Param("tenantId") UUID tenantId);

  // ═══════════════════════════════════════════════════════════════════════════
  // Date Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<SalesOrder> findByTenantIdAndOrderDateBetween(
      UUID tenantId, LocalDate startDate, LocalDate endDate);

  @Query(
      """
      SELECT o FROM SalesOrder o
      WHERE o.tenantId = :tenantId
      AND o.isActive = true
      AND o.promisedDeliveryDate < :date
      AND o.status NOT IN ('DELIVERED', 'CANCELLED')
      ORDER BY o.promisedDeliveryDate ASC
      """)
  List<SalesOrder> findOverdueOrders(
      @Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

  // ═══════════════════════════════════════════════════════════════════════════
  // Type Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<SalesOrder> findByTenantIdAndOrderType(UUID tenantId, OrderType orderType);

  // ═══════════════════════════════════════════════════════════════════════════
  // Pagination
  // ═══════════════════════════════════════════════════════════════════════════

  Page<SalesOrder> findByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

  Page<SalesOrder> findByTenantIdAndStatusAndIsActiveTrue(
      UUID tenantId, OrderStatus status, Pageable pageable);

  // ═══════════════════════════════════════════════════════════════════════════
  // Counts
  // ═══════════════════════════════════════════════════════════════════════════

  long countByTenantIdAndStatus(UUID tenantId, OrderStatus status);

  long countByTenantIdAndTradingPartnerIdAndIsActiveTrue(UUID tenantId, UUID tradingPartnerId);

  // ═══════════════════════════════════════════════════════════════════════════
  // Order Number Generation
  // ═══════════════════════════════════════════════════════════════════════════

  @Query(
      """
      SELECT MAX(o.orderNumber) FROM SalesOrder o
      WHERE o.tenantId = :tenantId
      AND o.orderNumber LIKE :prefix%
      """)
  Optional<String> findMaxOrderNumber(
      @Param("tenantId") UUID tenantId, @Param("prefix") String prefix);
}
