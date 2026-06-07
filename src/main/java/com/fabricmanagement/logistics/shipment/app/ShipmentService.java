package com.fabricmanagement.logistics.shipment.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.logistics.common.exception.LogisticsDomainException;
import com.fabricmanagement.logistics.shipment.domain.Shipment;
import com.fabricmanagement.logistics.shipment.domain.ShipmentLine;
import com.fabricmanagement.logistics.shipment.domain.ShipmentStatus;
import com.fabricmanagement.logistics.shipment.domain.ShipmentType;
import com.fabricmanagement.logistics.shipment.domain.event.ShipmentPickedUpEvent;
import com.fabricmanagement.logistics.shipment.dto.AddBatchToLineRequest;
import com.fabricmanagement.logistics.shipment.dto.AddShipmentLineRequest;
import com.fabricmanagement.logistics.shipment.dto.CreateShipmentRequest;
import com.fabricmanagement.logistics.shipment.dto.ShipmentDto;
import com.fabricmanagement.logistics.shipment.infra.repository.ShipmentRepository;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.production.execution.batch.api.facade.BatchFacade;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing shipments.
 *
 * <p>Uses TradingPartnerResolver for partner ID resolution (Faz 1.5 pattern). Supports both inbound
 * and outbound shipments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentService {

  private final ShipmentRepository shipmentRepository;
  private final TradingPartnerResolver partnerResolver;
  private final TradingPartnerService partnerService;
  private final DomainEventPublisher eventPublisher;
  private final BatchFacade batchFacade;

  private static final DateTimeFormatter SHIPMENT_NUMBER_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd");

  // ═══════════════════════════════════════════════════════════════════════════
  // CREATION
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Create a new shipment.
   *
   * @param request Shipment creation request
   * @return Created shipment DTO
   */
  @Transactional
  public ShipmentDto createShipment(CreateShipmentRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

    // Resolve partner ID (handles both new and legacy IDs)
    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, request.getPartnerId());

    // Generate shipment number
    String shipmentNumber = generateShipmentNumber(tenantId, request.getShipmentType());

    Shipment shipment =
        Shipment.builder()
            .tradingPartnerId(tradingPartnerId)
            .shipmentNumber(shipmentNumber)
            .orderReference(request.getOrderReference())
            .shipmentType(request.getShipmentType())
            .carrierName(request.getCarrierName())
            .carrierCode(request.getCarrierCode())
            .trackingNumber(request.getTrackingNumber())
            .shipDate(request.getShipDate())
            .estimatedDeliveryDate(request.getEstimatedDeliveryDate())
            .originAddress(request.getOriginAddress())
            .destinationAddress(request.getDestinationAddress())
            .totalWeight(request.getTotalWeight())
            .weightUnit(request.getWeightUnit())
            .packageCount(request.getPackageCount())
            .shippingCost(request.getShippingCost())
            .currency(request.getCurrency())
            .notes(request.getNotes())
            .metadata(request.getMetadata())
            .build();

    Shipment saved = shipmentRepository.save(shipment);

    // Get partner details for response
    TradingPartnerDto partner = partnerService.findById(tenantId, tradingPartnerId).orElse(null);

    log.info(
        "Shipment created: uid={}, partner={}, type={}",
        saved.getUid(),
        tradingPartnerId,
        saved.getShipmentType());
    return ShipmentDto.from(saved, partner);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find shipment by ID.
   *
   * @param shipmentId Shipment ID
   * @return Shipment DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<ShipmentDto> findById(UUID shipmentId) {
    UUID tenantId = TenantContext.requireTenantId();

    return shipmentRepository
        .findByTenantIdAndId(tenantId, shipmentId)
        .map(
            shipment -> {
              TradingPartnerDto partner =
                  partnerService.findById(tenantId, shipment.getTradingPartnerId()).orElse(null);
              return ShipmentDto.from(shipment, partner);
            });
  }

  /**
   * Find shipment by tracking number.
   *
   * @param trackingNumber Tracking number
   * @return Shipment DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<ShipmentDto> findByTrackingNumber(String trackingNumber) {
    return shipmentRepository.findByTrackingNumber(trackingNumber).map(ShipmentDto::from);
  }

  /**
   * Find shipments by partner ID.
   *
   * @param partnerId Partner ID (can be TradingPartner.id or legacy Company.id)
   * @return List of shipments
   */
  @Transactional(readOnly = true)
  public List<ShipmentDto> findByPartner(UUID partnerId) {
    UUID tenantId = TenantContext.requireTenantId();

    // Resolve partner ID
    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, partnerId);

    return shipmentRepository.findActiveByPartner(tenantId, tradingPartnerId).stream()
        .map(ShipmentDto::from)
        .toList();
  }

  /**
   * Find in-transit shipments by partner.
   *
   * @param partnerId Partner ID
   * @return List of in-transit shipments
   */
  @Transactional(readOnly = true)
  public List<ShipmentDto> findInTransitByPartner(UUID partnerId) {
    UUID tenantId = TenantContext.requireTenantId();

    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, partnerId);

    return shipmentRepository.findInTransitByPartner(tenantId, tradingPartnerId).stream()
        .map(ShipmentDto::from)
        .toList();
  }

  /**
   * Find shipments by status.
   *
   * @param status Shipment status
   * @return List of shipments
   */
  @Transactional(readOnly = true)
  public List<ShipmentDto> findByStatus(ShipmentStatus status) {
    UUID tenantId = TenantContext.requireTenantId();

    return shipmentRepository.findByTenantIdAndStatus(tenantId, status).stream()
        .map(ShipmentDto::from)
        .toList();
  }

  /**
   * Find all in-transit shipments.
   *
   * @return List of in-transit shipments
   */
  @Transactional(readOnly = true)
  public List<ShipmentDto> findInTransit() {
    UUID tenantId = TenantContext.requireTenantId();

    return shipmentRepository.findInTransit(tenantId).stream().map(ShipmentDto::from).toList();
  }

  /**
   * Find pending shipments.
   *
   * @return List of pending shipments
   */
  @Transactional(readOnly = true)
  public List<ShipmentDto> findPendingShipments() {
    UUID tenantId = TenantContext.requireTenantId();

    return shipmentRepository.findPendingShipments(tenantId).stream()
        .map(ShipmentDto::from)
        .toList();
  }

  /**
   * Find late shipments.
   *
   * @return List of late shipments
   */
  @Transactional(readOnly = true)
  public List<ShipmentDto> findLateShipments() {
    UUID tenantId = TenantContext.requireTenantId();

    return shipmentRepository.findLateShipments(tenantId, LocalDate.now()).stream()
        .map(ShipmentDto::from)
        .toList();
  }

  /**
   * Find outbound shipments.
   *
   * @return List of outbound shipments
   */
  @Transactional(readOnly = true)
  public List<ShipmentDto> findOutboundShipments() {
    UUID tenantId = TenantContext.requireTenantId();

    return shipmentRepository.findOutboundShipments(tenantId).stream()
        .map(ShipmentDto::from)
        .toList();
  }

  /**
   * Find inbound shipments.
   *
   * @return List of inbound shipments
   */
  @Transactional(readOnly = true)
  public List<ShipmentDto> findInboundShipments() {
    UUID tenantId = TenantContext.requireTenantId();

    return shipmentRepository.findInboundShipments(tenantId).stream()
        .map(ShipmentDto::from)
        .toList();
  }

  /**
   * Find shipments by order reference.
   *
   * @param orderReference Order reference
   * @return List of shipments
   */
  @Transactional(readOnly = true)
  public List<ShipmentDto> findByOrderReference(String orderReference) {
    UUID tenantId = TenantContext.requireTenantId();

    return shipmentRepository.findByTenantIdAndOrderReference(tenantId, orderReference).stream()
        .map(ShipmentDto::from)
        .toList();
  }

  /**
   * Get all shipments with pagination.
   *
   * @param pageable Pagination info
   * @return Page of shipments
   */
  @Transactional(readOnly = true)
  public Page<ShipmentDto> findAll(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();

    return shipmentRepository
        .findByTenantIdAndIsActiveTrue(tenantId, pageable)
        .map(ShipmentDto::from);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Start preparing a shipment.
   *
   * @param shipmentId Shipment ID
   * @return Updated shipment DTO
   */
  @Transactional
  public ShipmentDto startPreparing(UUID shipmentId) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.startPreparing();
    Shipment saved = shipmentRepository.save(shipment);

    log.info("Shipment preparing: uid={}", saved.getUid());
    return ShipmentDto.from(saved);
  }

  /**
   * Mark shipment as ready.
   *
   * @param shipmentId Shipment ID
   * @return Updated shipment DTO
   */
  @Transactional
  public ShipmentDto markReady(UUID shipmentId) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.markReady();
    Shipment saved = shipmentRepository.save(shipment);

    log.info("Shipment ready: uid={}", saved.getUid());
    return ShipmentDto.from(saved);
  }

  /**
   * Record pickup by carrier.
   *
   * @param shipmentId Shipment ID
   * @param carrierName Carrier name
   * @param trackingNumber Tracking number
   * @return Updated shipment DTO
   */
  @Transactional
  public ShipmentDto recordPickup(UUID shipmentId, String carrierName, String trackingNumber) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.recordPickup(carrierName, trackingNumber);
    Shipment saved = shipmentRepository.save(shipment);

    log.info("Shipment picked up: uid={}, tracking={}", saved.getUid(), trackingNumber);

    // Publish picking event for inventory transfer/deduction
    eventPublisher.publish(new ShipmentPickedUpEvent(tenantId, shipmentId));

    // Publish confirmed events for sales order update
    if (saved.getLines() != null) {
      saved.getLines().stream()
          .filter(line -> line.getSalesOrderLineId() != null)
          .filter(line -> line.totalLoadedQuantity().compareTo(java.math.BigDecimal.ZERO) > 0)
          .forEach(
              line ->
                  eventPublisher.publish(
                      new com.fabricmanagement.logistics.shipment.domain.event
                          .ShipmentLineConfirmedEvent(
                          tenantId,
                          line.getId(),
                          line.getSalesOrderLineId(),
                          line.totalLoadedQuantity())));
    }

    return ShipmentDto.from(saved);
  }

  /**
   * Mark shipment as in transit.
   *
   * @param shipmentId Shipment ID
   * @return Updated shipment DTO
   */
  @Transactional
  public ShipmentDto markInTransit(UUID shipmentId) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.inTransit();
    Shipment saved = shipmentRepository.save(shipment);

    log.info("Shipment in transit: uid={}", saved.getUid());
    return ShipmentDto.from(saved);
  }

  /**
   * Mark shipment as out for delivery.
   *
   * @param shipmentId Shipment ID
   * @return Updated shipment DTO
   */
  @Transactional
  public ShipmentDto markOutForDelivery(UUID shipmentId) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.outForDelivery();
    Shipment saved = shipmentRepository.save(shipment);

    log.info("Shipment out for delivery: uid={}", saved.getUid());
    return ShipmentDto.from(saved);
  }

  /**
   * Record delivery.
   *
   * @param shipmentId Shipment ID
   * @param recipientName Recipient name
   * @param deliveryProof Proof of delivery
   * @return Updated shipment DTO
   */
  @Transactional
  public ShipmentDto recordDelivery(UUID shipmentId, String recipientName, String deliveryProof) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.recordDelivery(recipientName, deliveryProof);
    Shipment saved = shipmentRepository.save(shipment);

    log.info("Shipment delivered: uid={}, recipient={}", saved.getUid(), recipientName);
    return ShipmentDto.from(saved);
  }

  /**
   * Record delivery failure.
   *
   * @param shipmentId Shipment ID
   * @param reason Failure reason
   * @return Updated shipment DTO
   */
  @Transactional
  public ShipmentDto recordDeliveryFailure(UUID shipmentId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.recordDeliveryFailure(reason);
    Shipment saved = shipmentRepository.save(shipment);

    log.info("Shipment delivery failed: uid={}, reason={}", saved.getUid(), reason);
    return ShipmentDto.from(saved);
  }

  /**
   * Cancel a shipment.
   *
   * @param shipmentId Shipment ID
   * @return Updated shipment DTO
   */
  @Transactional
  public ShipmentDto cancelShipment(UUID shipmentId) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.cancel();
    Shipment saved = shipmentRepository.save(shipment);

    log.info("Shipment cancelled: uid={}", saved.getUid());
    return ShipmentDto.from(saved);
  }

  /**
   * Update tracking info.
   *
   * @param shipmentId Shipment ID
   * @param trackingNumber Tracking number
   * @param trackingUrl Tracking URL
   * @return Updated shipment DTO
   */
  @Transactional
  public ShipmentDto updateTracking(UUID shipmentId, String trackingNumber, String trackingUrl) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.updateTracking(trackingNumber, trackingUrl);
    Shipment saved = shipmentRepository.save(shipment);

    log.info("Shipment tracking updated: uid={}, tracking={}", saved.getUid(), trackingNumber);
    return ShipmentDto.from(saved);
  }

  /**
   * Soft delete a shipment.
   *
   * @param shipmentId Shipment ID
   */
  @Transactional
  public void deleteShipment(UUID shipmentId) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);
    shipment.delete();
    shipmentRepository.save(shipment);

    log.info("Shipment deleted (soft): uid={}", shipment.getUid());
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // HELPERS
  // ═══════════════════════════════════════════════════════════════════════════

  @Transactional
  public ShipmentDto addShipmentLine(UUID shipmentId, AddShipmentLineRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Adding line to shipment {}: {}", shipmentId, request);

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);

    // Prevent modification if shipment is beyond READY
    if (shipment.getStatus() != ShipmentStatus.PENDING
        && shipment.getStatus() != ShipmentStatus.PREPARING
        && shipment.getStatus() != ShipmentStatus.READY) {
      throw new LogisticsDomainException(
          "Cannot add lines to shipment in status " + shipment.getStatus());
    }

    ShipmentLine line =
        ShipmentLine.builder()
            .shipmentId(shipmentId)
            .lineNumber(request.getLineNumber())
            .salesOrderLineId(request.getSalesOrderLineId())
            .quantity(request.getQuantity())
            .unit(request.getUnit())
            .build();

    shipment.addLine(line);
    Shipment saved = shipmentRepository.save(shipment);

    return ShipmentDto.from(saved);
  }

  @Transactional
  public ShipmentDto addBatchToLine(
      UUID shipmentId, UUID shipmentLineId, AddBatchToLineRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

    Shipment shipment = getShipmentOrThrow(tenantId, shipmentId);

    ShipmentLine line =
        shipment.getLines().stream()
            .filter(l -> l.getId().equals(shipmentLineId))
            .findFirst()
            .orElseThrow(
                () -> new LogisticsDomainException("Shipment line not found: " + shipmentLineId));

    // Idempotency check
    boolean alreadyAdded =
        line.getBatches().stream().anyMatch(b -> b.getBatchId().equals(request.getBatchId()));
    if (alreadyAdded) {
      log.info("Batch {} already added to line {}", request.getBatchId(), shipmentLineId);
      return ShipmentDto.from(shipment);
    }

    // Cross-module QC Check
    BatchDto batch =
        batchFacade
            .getById(request.getBatchId())
            .orElseThrow(
                () ->
                    new LogisticsDomainException(
                        "Batch not found in production module: " + request.getBatchId()));

    // Check if QC Approved (AVAILABLE corresponds to QC approved in batch module)
    if (batch.getStatus()
        != com.fabricmanagement.production.execution.batch.domain.BatchStatus.AVAILABLE) {
      throw new LogisticsDomainException(
          "Cannot add batch to shipment: QC status is not APPROVED (AVAILABLE). Current status: "
              + batch.getStatus());
    }

    // Extract Grade if any
    String grade = null;
    if (batch.getAttributes() != null && batch.getAttributes().containsKey("fiber_grade")) {
      grade = String.valueOf(batch.getAttributes().get("fiber_grade"));
    }

    line.addBatch(request.getBatchId(), request.getQuantity(), grade);

    Shipment saved = shipmentRepository.save(shipment);

    log.info("Batch {} added to ShipmentLine {}", request.getBatchId(), shipmentLineId);
    return ShipmentDto.from(saved);
  }

  private Shipment getShipmentOrThrow(UUID tenantId, UUID shipmentId) {
    return shipmentRepository
        .findByTenantIdAndId(tenantId, shipmentId)
        .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
  }

  private String generateShipmentNumber(UUID tenantId, ShipmentType type) {
    String prefix =
        (type.isOutbound() ? "SHP" : "RCV")
            + "-"
            + LocalDate.now().format(SHIPMENT_NUMBER_DATE_FORMAT)
            + "-";
    String maxNumber =
        shipmentRepository.findMaxShipmentNumber(tenantId, prefix).orElse(prefix + "00000");

    // Extract sequence number and increment
    String sequencePart = maxNumber.substring(prefix.length());
    int sequence = Integer.parseInt(sequencePart) + 1;

    return prefix + String.format("%05d", sequence);
  }
}
