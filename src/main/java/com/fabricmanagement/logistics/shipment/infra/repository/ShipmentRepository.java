package com.fabricmanagement.logistics.shipment.infra.repository;

import com.fabricmanagement.logistics.shipment.domain.Shipment;
import com.fabricmanagement.logistics.shipment.domain.ShipmentStatus;
import com.fabricmanagement.logistics.shipment.domain.ShipmentType;
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
 * Repository for Shipment entity.
 *
 * <p>All queries are tenant-scoped for data isolation.
 */
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

  // ═══════════════════════════════════════════════════════════════════════════
  // Basic Lookups
  // ═══════════════════════════════════════════════════════════════════════════

  Optional<Shipment> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Shipment> findByTenantIdAndUid(UUID tenantId, String uid);

  Optional<Shipment> findByTenantIdAndShipmentNumber(UUID tenantId, String shipmentNumber);

  Optional<Shipment> findByTrackingNumber(String trackingNumber);

  // ═══════════════════════════════════════════════════════════════════════════
  // TradingPartner Queries (Faz 1.5)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Find shipments by trading partner. */
  List<Shipment> findByTenantIdAndTradingPartnerId(UUID tenantId, UUID tradingPartnerId);

  /** Find active shipments by trading partner. */
  @Query(
      """
      SELECT s FROM Shipment s
      WHERE s.tenantId = :tenantId
      AND s.tradingPartnerId = :partnerId
      AND s.isActive = true
      ORDER BY s.shipDate DESC
      """)
  List<Shipment> findActiveByPartner(
      @Param("tenantId") UUID tenantId, @Param("partnerId") UUID partnerId);

  /** Find in-transit shipments by trading partner. */
  @Query(
      """
      SELECT s FROM Shipment s
      WHERE s.tenantId = :tenantId
      AND s.tradingPartnerId = :partnerId
      AND s.isActive = true
      AND s.status IN ('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY')
      ORDER BY s.estimatedDeliveryDate ASC
      """)
  List<Shipment> findInTransitByPartner(
      @Param("tenantId") UUID tenantId, @Param("partnerId") UUID partnerId);

  /** Find shipments by trading partner with pagination. */
  Page<Shipment> findByTenantIdAndTradingPartnerIdAndIsActiveTrue(
      UUID tenantId, UUID tradingPartnerId, Pageable pageable);

  // ═══════════════════════════════════════════════════════════════════════════
  // Status Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<Shipment> findByTenantIdAndStatus(UUID tenantId, ShipmentStatus status);

  List<Shipment> findByTenantIdAndStatusIn(UUID tenantId, List<ShipmentStatus> statuses);

  /** Find all in-transit shipments. */
  @Query(
      """
      SELECT s FROM Shipment s
      WHERE s.tenantId = :tenantId
      AND s.isActive = true
      AND s.status IN ('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY')
      ORDER BY s.estimatedDeliveryDate ASC
      """)
  List<Shipment> findInTransit(@Param("tenantId") UUID tenantId);

  /** Find pending shipments (not yet dispatched). */
  @Query(
      """
      SELECT s FROM Shipment s
      WHERE s.tenantId = :tenantId
      AND s.isActive = true
      AND s.status IN ('PENDING', 'PREPARING', 'READY')
      ORDER BY s.shipDate ASC
      """)
  List<Shipment> findPendingShipments(@Param("tenantId") UUID tenantId);

  /** Find late shipments. */
  @Query(
      """
      SELECT s FROM Shipment s
      WHERE s.tenantId = :tenantId
      AND s.isActive = true
      AND s.estimatedDeliveryDate < :today
      AND s.status NOT IN ('DELIVERED', 'RETURNED', 'CANCELLED')
      ORDER BY s.estimatedDeliveryDate ASC
      """)
  List<Shipment> findLateShipments(
      @Param("tenantId") UUID tenantId, @Param("today") LocalDate today);

  // ═══════════════════════════════════════════════════════════════════════════
  // Type Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<Shipment> findByTenantIdAndShipmentType(UUID tenantId, ShipmentType shipmentType);

  /** Find outbound shipments. */
  @Query(
      """
      SELECT s FROM Shipment s
      WHERE s.tenantId = :tenantId
      AND s.isActive = true
      AND s.shipmentType IN ('OUTBOUND', 'RETURN_OUTBOUND')
      ORDER BY s.shipDate DESC
      """)
  List<Shipment> findOutboundShipments(@Param("tenantId") UUID tenantId);

  /** Find inbound shipments. */
  @Query(
      """
      SELECT s FROM Shipment s
      WHERE s.tenantId = :tenantId
      AND s.isActive = true
      AND s.shipmentType IN ('INBOUND', 'RETURN_INBOUND')
      ORDER BY s.estimatedDeliveryDate ASC
      """)
  List<Shipment> findInboundShipments(@Param("tenantId") UUID tenantId);

  // ═══════════════════════════════════════════════════════════════════════════
  // Carrier Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<Shipment> findByTenantIdAndCarrierCode(UUID tenantId, String carrierCode);

  // ═══════════════════════════════════════════════════════════════════════════
  // Date Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<Shipment> findByTenantIdAndShipDateBetween(
      UUID tenantId, LocalDate startDate, LocalDate endDate);

  List<Shipment> findByTenantIdAndEstimatedDeliveryDateBetween(
      UUID tenantId, LocalDate startDate, LocalDate endDate);

  // ═══════════════════════════════════════════════════════════════════════════
  // Order Reference Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<Shipment> findByTenantIdAndOrderReference(UUID tenantId, String orderReference);

  // ═══════════════════════════════════════════════════════════════════════════
  // Pagination
  // ═══════════════════════════════════════════════════════════════════════════

  Page<Shipment> findByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

  Page<Shipment> findByTenantIdAndStatusAndIsActiveTrue(
      UUID tenantId, ShipmentStatus status, Pageable pageable);

  Page<Shipment> findByTenantIdAndShipmentTypeAndIsActiveTrue(
      UUID tenantId, ShipmentType shipmentType, Pageable pageable);

  // ═══════════════════════════════════════════════════════════════════════════
  // Counts
  // ═══════════════════════════════════════════════════════════════════════════

  long countByTenantIdAndStatus(UUID tenantId, ShipmentStatus status);

  long countByTenantIdAndTradingPartnerIdAndIsActiveTrue(UUID tenantId, UUID tradingPartnerId);

  // ═══════════════════════════════════════════════════════════════════════════
  // Shipment Number Generation
  // ═══════════════════════════════════════════════════════════════════════════

  @Query(
      """
      SELECT MAX(s.shipmentNumber) FROM Shipment s
      WHERE s.tenantId = :tenantId
      AND s.shipmentNumber LIKE :prefix%
      """)
  Optional<String> findMaxShipmentNumber(
      @Param("tenantId") UUID tenantId, @Param("prefix") String prefix);
}
