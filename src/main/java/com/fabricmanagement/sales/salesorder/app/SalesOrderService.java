package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineResponse;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing sales orders.
 *
 * <p>Uses TradingPartnerResolver for partner ID resolution (Faz 1.5 pattern). Supports both new
 * TradingPartner IDs and legacy Company IDs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalesOrderService {

  private final SalesOrderRepository orderRepository;
  private final TradingPartnerResolver partnerResolver;
  private final TradingPartnerService partnerService;
  private final SalesOrderLineRepository lineRepository;
  private final SalesOrderRuleEngine ruleEngine;
  private final ModuleSpecsValidator moduleSpecsValidator;
  private final DomainEventPublisher domainEventPublisher;
  private final DocumentNumberGenerator documentNumberGenerator;

  // ═══════════════════════════════════════════════════════════════════════════
  // CREATION
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Create a new sales order.
   *
   * @param request Order creation request
   * @return Created order DTO
   */
  @Transactional
  public SalesOrderDto createOrder(CreateSalesOrderRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // Resolve partner ID (handles both new and legacy IDs)
    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, request.getPartnerId());

    // Generate order number
    LocalDate effectiveDate =
        request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now();
    String orderNumber = generateOrderNumber(tenantId, effectiveDate);

    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(tradingPartnerId)
            .orderNumber(orderNumber)
            .customerReference(request.getCustomerReference())
            .orderType(request.getOrderType())
            .orderDate(request.getOrderDate())
            .requestedDeliveryDate(request.getRequestedDeliveryDate())
            .promisedDeliveryDate(request.getPromisedDeliveryDate())
            .totals(
                OrderTotals.zero(request.getCurrency() != null ? request.getCurrency() : "TRY")
                    .withTotalAmount(
                        request.getTotalAmount() != null
                            ? Money.of(
                                request.getTotalAmount(),
                                request.getCurrency() != null ? request.getCurrency() : "TRY")
                            : Money.zero(
                                request.getCurrency() != null ? request.getCurrency() : "TRY"))
                    .withTaxAmount(
                        request.getTaxAmount() != null
                            ? Money.of(
                                request.getTaxAmount(),
                                request.getCurrency() != null ? request.getCurrency() : "TRY")
                            : Money.zero(
                                request.getCurrency() != null ? request.getCurrency() : "TRY"))
                    .withDiscountAmount(
                        request.getDiscountAmount() != null
                            ? Money.of(
                                request.getDiscountAmount(),
                                request.getCurrency() != null ? request.getCurrency() : "TRY")
                            : Money.zero(
                                request.getCurrency() != null ? request.getCurrency() : "TRY")))
            .shippingAddress(request.getShippingAddress())
            .billingAddress(request.getBillingAddress())
            .shippingMethod(request.getShippingMethod())
            .notes(request.getNotes())
            .metadata(request.getMetadata())
            .moduleType(request.getModuleType())
            .deadline(request.getDeadline())
            .quoteId(request.getQuoteId())
            .sampleRequestId(request.getSampleRequestId())
            .build();

    SalesOrder saved = orderRepository.save(order);

    // Persist embedded lines (validated + moduleSpecs checked)
    if (request.getLines() != null && !request.getLines().isEmpty()) {
      List<SalesOrderLine> lines =
          request.getLines().stream()
              .map(
                  lineReq -> {
                    moduleSpecsValidator.validate(lineReq);
                    return mapLineRequestToEntity(lineReq, saved.getId());
                  })
              .toList();
      lineRepository.saveAll(lines);
    }

    // Get partner details for response
    TradingPartnerDto partner = partnerService.findById(tenantId, tradingPartnerId).orElse(null);

    log.info(
        "Sales order created: uid={}, partner={}, lines={}",
        saved.getUid(),
        tradingPartnerId,
        request.getLines() == null ? 0 : request.getLines().size());
    return SalesOrderDto.from(saved, partner);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find order by ID.
   *
   * @param orderId Order ID
   * @return Order DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<SalesOrderDto> findById(UUID orderId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return orderRepository
        .findByTenantIdAndId(tenantId, orderId)
        .map(
            order -> {
              TradingPartnerDto partner =
                  partnerService.findById(tenantId, order.getTradingPartnerId()).orElse(null);
              // Load embedded lines for detail view
              List<SalesOrderLineResponse> lineResponses =
                  lineRepository
                      .findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId())
                      .stream()
                      .map(this::mapLineToResponse)
                      .toList();
              return SalesOrderDto.from(order, partner, lineResponses);
            });
  }

  /**
   * Find order by order number.
   *
   * @param orderNumber Order number
   * @return Order DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<SalesOrderDto> findByOrderNumber(String orderNumber) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return orderRepository
        .findByTenantIdAndOrderNumber(tenantId, orderNumber)
        .map(SalesOrderDto::from);
  }

  /**
   * Find orders by partner ID.
   *
   * @param partnerId Partner ID (can be TradingPartner.id or legacy Company.id)
   * @return List of orders
   */
  @Transactional(readOnly = true)
  public List<SalesOrderDto> findByPartner(UUID partnerId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // Resolve partner ID to ensure we query with the correct ID
    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, partnerId);

    return orderRepository.findActiveByPartner(tenantId, tradingPartnerId).stream()
        .map(SalesOrderDto::from)
        .toList();
  }

  /**
   * Find orders by status.
   *
   * @param status Order status
   * @return List of orders
   */
  @Transactional(readOnly = true)
  public List<SalesOrderDto> findByStatus(OrderStatus status) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return orderRepository.findByTenantIdAndStatus(tenantId, status).stream()
        .map(SalesOrderDto::from)
        .toList();
  }

  /**
   * Find open orders (not in terminal status).
   *
   * @return List of open orders
   */
  @Transactional(readOnly = true)
  public List<SalesOrderDto> findOpenOrders() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return orderRepository.findOpenOrders(tenantId).stream().map(SalesOrderDto::from).toList();
  }

  /**
   * Find overdue orders.
   *
   * @return List of overdue orders
   */
  @Transactional(readOnly = true)
  public List<SalesOrderDto> findOverdueOrders() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return orderRepository.findOverdueOrders(tenantId, LocalDate.now()).stream()
        .map(SalesOrderDto::from)
        .toList();
  }

  /**
   * Get all orders with pagination.
   *
   * @param pageable Pagination info
   * @return Page of orders
   */
  @Transactional(readOnly = true)
  public Page<SalesOrderDto> findAll(Pageable pageable) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return orderRepository
        .findByTenantIdAndIsActiveTrue(tenantId, pageable)
        .map(SalesOrderDto::from);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Confirm an order.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto confirmOrder(UUID orderId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    order.confirm();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order confirmed: uid={}", saved.getUid());

    // Faz 2.2 — trigger RuleEngine: recipe matching + WorkOrder DRAFT creation per line
    ruleEngine.processConfirmedOrder(saved);

    List<SalesOrderLine> orderLines =
        lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(saved.getId());

    BigDecimal totalQuantity =
        orderLines.stream()
            .map(SalesOrderLine::getRequestedQty)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<SalesOrderConfirmedEvent.SalesOrderLineSnapshot> snapshotLines =
        orderLines.stream()
            .map(
                line ->
                    new SalesOrderConfirmedEvent.SalesOrderLineSnapshot(
                        line.getId(),
                        line.getProductId(),
                        line.getProductDesc() != null
                            ? line.getProductDesc()
                            : "PRODUCT_" + line.getProductId(), // Default product identifier
                        line.getRequestedQty(),
                        line.getUnit(),
                        saved.getRequestedDeliveryDate() // Parent order requested delivery date
                        ))
            .toList();

    domainEventPublisher.publish(
        new SalesOrderConfirmedEvent(
            tenantId,
            saved.getId(),
            saved.getOrderNumber(),
            null,
            null,
            totalQuantity,
            null,
            saved.getRequestedDeliveryDate(),
            snapshotLines));

    TradingPartnerDto partner =
        partnerService.findById(tenantId, saved.getTradingPartnerId()).orElse(null);
    List<SalesOrderLineResponse> lineResponses =
        orderLines.stream().map(this::mapLineToResponse).toList();
    return SalesOrderDto.from(saved, partner, lineResponses);
  }

  /**
   * Start processing an order.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto startProcessing(UUID orderId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    order.startProcessing();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order processing started: uid={}", saved.getUid());
    return SalesOrderDto.from(saved);
  }

  /**
   * Ship an order.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto shipOrder(UUID orderId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    order.ship();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order shipped: uid={}", saved.getUid());
    return SalesOrderDto.from(saved);
  }

  /**
   * Deliver an order.
   *
   * @param orderId Order ID
   * @param deliveryDate Actual delivery date
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto deliverOrder(UUID orderId, LocalDate deliveryDate) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    order.deliver(deliveryDate);
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order delivered: uid={}", saved.getUid());
    return SalesOrderDto.from(saved);
  }

  /**
   * Cancel an order.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto cancelOrder(UUID orderId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    order.cancel();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order cancelled: uid={}", saved.getUid());
    return SalesOrderDto.from(saved);
  }

  /**
   * Soft delete an order.
   *
   * @param orderId Order ID
   */
  @Transactional
  public void deleteOrder(UUID orderId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);

    // Cascade soft-delete all active lines first
    List<SalesOrderLine> lines =
        lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId());
    lines.forEach(SalesOrderLine::delete);
    if (!lines.isEmpty()) {
      lineRepository.saveAll(lines);
      log.info("Soft-deleted {} SalesOrderLine(s) for order uid={}", lines.size(), order.getUid());
    }

    order.delete();
    orderRepository.save(order);

    log.info("Sales order deleted (soft): uid={}", order.getUid());
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // HELPERS
  // ═══════════════════════════════════════════════════════════════════════════

  private SalesOrder getOrderOrThrow(UUID tenantId, UUID orderId) {
    return orderRepository
        .findByTenantIdAndId(tenantId, orderId)
        .orElseThrow(
            () ->
                new com.fabricmanagement.sales.common.exception.OrderDomainException(
                    "Sales order not found: " + orderId));
  }

  // ── Line mapping helpers ─────────────────────────────────────────────────

  private SalesOrderLine mapLineRequestToEntity(SalesOrderLineRequest req, UUID salesOrderId) {
    return SalesOrderLine.builder()
        .salesOrderId(salesOrderId)
        .productId(req.getProductId())
        .productDesc(req.getProductDesc())
        .requestedQty(req.getRequestedQty())
        .unit(req.getUnit())
        .unitPrice(
            req.getUnitPrice() != null && req.getCurrency() != null
                ? com.fabricmanagement.common.util.Money.of(req.getUnitPrice(), req.getCurrency())
                : null)
        .moduleType(req.getModuleType())
        .moduleSpecs(req.getModuleSpecs())
        .lineStatus(SalesOrderLineStatus.PENDING)
        .build();
  }

  private SalesOrderLineResponse mapLineToResponse(SalesOrderLine line) {
    return SalesOrderLineResponse.builder()
        .id(line.getId())
        .uid(line.getUid())
        .salesOrderId(line.getSalesOrderId())
        .productId(line.getProductId())
        .productDesc(line.getProductDesc())
        .requestedQty(line.getRequestedQty())
        .unit(line.getUnit())
        .unitPrice(line.getUnitPrice() != null ? line.getUnitPrice().getAmount() : null)
        .currency(line.getCurrency())
        .moduleType(line.getModuleType())
        .moduleSpecs(line.getModuleSpecs())
        .lineStatus(line.getLineStatus())
        .recipeId(line.getRecipeId())
        .build();
  }

  private String generateOrderNumber(UUID tenantId, LocalDate orderDate) {
    return documentNumberGenerator.generate(tenantId, "SALES_ORDER", "SO", orderDate, 5);
  }
}
