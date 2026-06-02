package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.CurrencyMismatchException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderUpdateCommand;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineResponse;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
  private final ApprovalPort approvalPort;

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

    String currency = request.getCurrency() != null ? request.getCurrency() : "TRY";
    BigDecimal calculatedTotal = calculateCreateTotal(request.getLines(), currency);
    validateDiscountDoesNotExceedTotal(calculatedTotal, request.getDiscountAmount());

    Money total = Money.of(calculatedTotal, currency);
    Money tax =
        request.getTaxAmount() != null
            ? Money.of(request.getTaxAmount(), currency)
            : Money.zero(currency);
    Money discount =
        request.getDiscountAmount() != null
            ? Money.of(request.getDiscountAmount(), currency)
            : Money.zero(currency);

    OrderTotals totals = OrderTotals.of(total, tax, discount);

    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(tradingPartnerId)
            .orderNumber(orderNumber)
            .customerReference(request.getCustomerReference())
            .orderType(request.getOrderType())
            .orderDate(request.getOrderDate())
            .requestedDeliveryDate(request.getRequestedDeliveryDate())
            .promisedDeliveryDate(request.getPromisedDeliveryDate())
            .totals(totals)
            .shippingAddress(request.getShippingAddress())
            .billingAddress(request.getBillingAddress())
            .shippingMethod(request.getShippingMethod())
            .notes(request.getNotes())
            .metadata(request.getMetadata())
            .moduleType(deriveOrderModuleTypeFromRequests(request.getLines()))
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
                    return mapLineRequestToEntity(lineReq, saved.getId(), saved.getCurrency());
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
  // UPDATE
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Update a draft sales order. Uses full-replace strategy for lines.
   *
   * @param orderId Order ID
   * @param request Update request with version for optimistic locking
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto updateOrder(UUID orderId, UpdateSalesOrderRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // 1. Fetch managed entity (Hibernate loads version)
    SalesOrder order = getOrderOrThrow(tenantId, orderId);

    // 2. Optimistic lock — compare, DON'T setVersion
    if (!order.getVersion().equals(request.getVersion())) {
      throw new ObjectOptimisticLockingFailureException(SalesOrder.class.getSimpleName(), orderId);
    }

    // 3. Build OrderTotals — totalAmount hesaplanacak, tax/discount request'ten
    String currency = request.getCurrency();

    // 4. Line sync (full-replace)
    List<SalesOrderLine> existingLines =
        lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId());

    Set<UUID> incomingLineIds =
        request.getLines().stream()
            .map(UpdateSalesOrderLineRequest::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    // 4a. Soft-delete lines not in request
    existingLines.stream()
        .filter(line -> !incomingLineIds.contains(line.getId()))
        .forEach(SalesOrderLine::delete);

    // 4b. Update existing + create new lines
    List<SalesOrderLine> syncedLines = new ArrayList<>();
    for (UpdateSalesOrderLineRequest lineReq : request.getLines()) {
      moduleSpecsValidator.validate(lineReq);

      if (lineReq.getId() != null) {
        // Update existing
        SalesOrderLine existing =
            existingLines.stream()
                .filter(l -> l.getId().equals(lineReq.getId()))
                .findFirst()
                .orElseThrow(() -> new OrderDomainException("Line not found: " + lineReq.getId()));
        updateLineFromRequest(existing, lineReq, currency);
        syncedLines.add(existing);
      } else {
        // Create new
        SalesOrderLine newLine = mapUpdateLineRequestToEntity(lineReq, order.getId(), currency);
        syncedLines.add(lineRepository.save(newLine));
      }
    }

    // 5. Calculate totalAmount from lines (AGENTS.md: "Hesapla, input'a güvenme")
    BigDecimal calculatedTotal =
        syncedLines.stream()
            .filter(l -> l.getIsActive() && l.getUnitPrice() != null)
            .map(l -> l.getUnitPrice().getAmount().multiply(l.getRequestedQty()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    validateDiscountDoesNotExceedTotal(calculatedTotal, request.getDiscountAmount());

    Money total = Money.of(calculatedTotal, currency);
    Money tax =
        request.getTaxAmount() != null
            ? Money.of(request.getTaxAmount(), currency)
            : Money.zero(currency);
    Money discount =
        request.getDiscountAmount() != null
            ? Money.of(request.getDiscountAmount(), currency)
            : Money.zero(currency);

    OrderTotals totals = OrderTotals.of(total, tax, discount);

    // 6. Build domain command and apply
    SalesOrderUpdateCommand cmd =
        new SalesOrderUpdateCommand(
            request.getCustomerReference(),
            request.getOrderDate(),
            request.getRequestedDeliveryDate(),
            request.getPromisedDeliveryDate(),
            totals,
            request.getShippingAddress(),
            request.getBillingAddress(),
            request.getShippingMethod(),
            request.getNotes(),
            request.getMetadata(),
            deriveOrderModuleType(syncedLines),
            request.getDeadline());

    order.updateDraft(cmd); // throws 409 if not DRAFT
    SalesOrder saved = orderRepository.save(order);

    // 7. Response
    TradingPartnerDto partner =
        partnerService.findById(tenantId, saved.getTradingPartnerId()).orElse(null);
    List<SalesOrderLineResponse> lineResponses =
        syncedLines.stream()
            .filter(SalesOrderLine::getIsActive)
            .map(this::mapLineToResponse)
            .toList();

    log.info(
        "Sales order updated: uid={}, linesAdded={}, linesRemoved={}",
        saved.getUid(),
        request.getLines().stream().filter(l -> l.getId() == null).count(),
        existingLines.size() - incomingLineIds.size());

    return SalesOrderDto.from(saved, partner, lineResponses);
  }

  private void updateLineFromRequest(
      SalesOrderLine line, UpdateSalesOrderLineRequest req, String orderCurrency) {
    validateLineCurrency(req.getUnitPrice(), req.getCurrency(), orderCurrency);
    line.setProductId(req.getProductId());
    line.setProductDesc(req.getProductDesc());
    line.setRequestedQty(req.getRequestedQty());
    line.setUnit(req.getUnit());

    // Note: unitPrice can be null for draft/pending lines
    Money newUnitPrice =
        req.getUnitPrice() != null && req.getCurrency() != null
            ? Money.of(req.getUnitPrice(), req.getCurrency())
            : null;
    line.updateUnitPrice(newUnitPrice);

    line.setModuleType(req.getModuleType());
    line.setModuleSpecs(req.getModuleSpecs());
  }

  private SalesOrderLine mapUpdateLineRequestToEntity(
      UpdateSalesOrderLineRequest request, UUID orderId, String orderCurrency) {
    validateLineCurrency(request.getUnitPrice(), request.getCurrency(), orderCurrency);

    // Note: unitPrice can be null for draft/pending lines
    Money unitPrice =
        request.getUnitPrice() != null && request.getCurrency() != null
            ? Money.of(request.getUnitPrice(), request.getCurrency())
            : null;

    return SalesOrderLine.builder()
        .salesOrderId(orderId)
        .productId(request.getProductId())
        .productDesc(request.getProductDesc())
        .requestedQty(request.getRequestedQty())
        .unit(request.getUnit())
        .unitPrice(unitPrice)
        .lineStatus(SalesOrderLineStatus.PENDING)
        .moduleType(request.getModuleType())
        .moduleSpecs(request.getModuleSpecs())
        .build();
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

    if (order.getStatus() == OrderStatus.PENDING_APPROVAL) {
      throw new com.fabricmanagement.sales.common.exception.OrderDomainException(
          "Order is awaiting approval; cannot be confirmed manually", 409);
    }

    if (order.getStatus() == OrderStatus.DRAFT) {
      boolean needsApproval =
          approvalPort.requiresApproval(
              tenantId,
              TenantContext.getCurrentUserId(),
              "SALES_ORDER",
              order.getId(),
              order.getTotals() != null
                  ? order.getTotals().calculateGrandTotal().getAmount()
                  : null,
              order.getCurrency());

      if (needsApproval) {
        log.info(
            "Sales order {} requires approval, moving to PENDING_APPROVAL", order.getOrderNumber());
        order.pendingApproval();
        SalesOrder saved = orderRepository.save(order);
        TradingPartnerDto partner =
            partnerService.findById(tenantId, saved.getTradingPartnerId()).orElse(null);
        List<SalesOrderLineResponse> lineResponses =
            lineRepository
                .findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(saved.getId())
                .stream()
                .map(this::mapLineToResponse)
                .toList();
        return SalesOrderDto.from(saved, partner, lineResponses);
      }
    }

    order.confirm();
    return finalizeConfirmation(order, tenantId);
  }

  /**
   * Tüm aktif satırlar aynı birime sahipse o birimi döner, farklı birimler varsa veya satır yoksa
   * null döner.
   */
  private String deriveOrderUnit(List<SalesOrderLine> lines) {
    if (lines.isEmpty()) {
      return null;
    }
    Set<String> units =
        lines.stream()
            .map(SalesOrderLine::getUnit)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    return units.size() == 1 ? units.iterator().next() : null;
  }

  /**
   * Tüm aktif satırlar aynı moduleType'a sahipse o değeri döner; satır yoksa veya karışık
   * moduleType varsa null döner. Null line moduleType değerleri deriveOrderUnit ile tutarlı şekilde
   * yok sayılır.
   */
  private ModuleType deriveOrderModuleType(List<SalesOrderLine> lines) {
    if (lines.isEmpty()) {
      return null;
    }
    Set<ModuleType> moduleTypes =
        lines.stream()
            .filter(line -> Boolean.TRUE.equals(line.getIsActive()))
            .map(SalesOrderLine::getModuleType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    return moduleTypes.size() == 1 ? moduleTypes.iterator().next() : null;
  }

  /** Confirm an order as SystemUser (called after approval callback). */
  @Transactional
  public SalesOrderDto confirmOrderAsSystem(UUID orderId) {
    return TenantContext.executeInTenantContext(
        TenantContext.getCurrentTenantId(),
        () -> {
          TenantContext.setCurrentUserId(com.fabricmanagement.platform.user.domain.SystemUser.ID);
          UUID tenantId = TenantContext.getCurrentTenantId();
          SalesOrder order = getOrderOrThrow(tenantId, orderId);
          order.confirmFromApproval();
          return finalizeConfirmation(order, tenantId);
        });
  }

  private SalesOrderDto finalizeConfirmation(SalesOrder order, UUID tenantId) {
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order confirmed: uid={}", saved.getUid());

    // Faz 2.2 — trigger RuleEngine: recipe matching + WorkOrder DRAFT creation per line
    ruleEngine.processConfirmedOrder(saved);

    TradingPartnerDto partner =
        partnerService.findById(tenantId, saved.getTradingPartnerId()).orElse(null);

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

    UUID customerId = saved.getTradingPartnerId();
    String customerName = partner != null ? partner.getDisplayName() : null;
    String unit = deriveOrderUnit(orderLines);

    domainEventPublisher.publish(
        new SalesOrderConfirmedEvent(
            tenantId,
            saved.getId(),
            saved.getOrderNumber(),
            customerId,
            customerName,
            totalQuantity,
            unit,
            saved.getRequestedDeliveryDate(),
            snapshotLines));

    List<SalesOrderLineResponse> lineResponses =
        orderLines.stream().map(this::mapLineToResponse).toList();
    return SalesOrderDto.from(saved, partner, lineResponses);
  }

  /** Reject an order (called after approval rejection callback). */
  @Transactional
  public void rejectOrder(UUID orderId, String reason) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    order.reject(reason);
    orderRepository.save(order);
    log.info("Sales order rejected: uid={}, reason={}", order.getUid(), reason);
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

    // Collect active line IDs for cascade notification before cancellation
    List<UUID> activeLineIds =
        lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()).stream()
            .map(SalesOrderLine::getId)
            .toList();

    order.cancel();
    SalesOrder saved = orderRepository.save(order);

    domainEventPublisher.publish(
        new SalesOrderCancelledEvent(
            tenantId, saved.getId(), saved.getOrderNumber(), activeLineIds));

    log.info("Sales order cancelled: uid={}, lineCount={}", saved.getUid(), activeLineIds.size());
    return SalesOrderDto.from(saved);
  }

  /**
   * Put an order on hold.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto holdOrder(UUID orderId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    order.hold();
    SalesOrder saved = orderRepository.save(order);

    log.info(
        "Sales order put on hold: uid={}, previousStatus={}",
        saved.getUid(),
        saved.getStatusBeforeHold());
    return SalesOrderDto.from(saved);
  }

  /**
   * Resume an order from ON_HOLD.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto resumeOrder(UUID orderId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    order.resume();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order resumed: uid={}, restoredStatus={}", saved.getUid(), saved.getStatus());
    return SalesOrderDto.from(saved);
  }

  /**
   * Revise a rejected order back to DRAFT.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto reviseOrder(UUID orderId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    order.reviseRejected();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order revised to DRAFT: uid={}", saved.getUid());
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

  private BigDecimal calculateCreateTotal(List<SalesOrderLineRequest> lines, String orderCurrency) {
    if (lines == null || lines.isEmpty()) {
      return BigDecimal.ZERO;
    }

    return lines.stream()
        .peek(line -> validateLineCurrency(line.getUnitPrice(), line.getCurrency(), orderCurrency))
        .filter(line -> line.getUnitPrice() != null)
        .map(line -> line.getUnitPrice().multiply(line.getRequestedQty()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private ModuleType deriveOrderModuleTypeFromRequests(List<SalesOrderLineRequest> lines) {
    if (lines == null || lines.isEmpty()) {
      return null;
    }
    Set<ModuleType> moduleTypes =
        lines.stream()
            .map(SalesOrderLineRequest::getModuleType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    return moduleTypes.size() == 1 ? moduleTypes.iterator().next() : null;
  }

  private void validateDiscountDoesNotExceedTotal(
      BigDecimal calculatedTotal, BigDecimal discountAmount) {
    if (discountAmount != null && discountAmount.compareTo(calculatedTotal) > 0) {
      throw new OrderDomainException("Discount amount cannot exceed calculated order total");
    }
  }

  private void validateLineCurrency(
      BigDecimal unitPrice, String lineCurrency, String orderCurrency) {
    if (unitPrice != null && lineCurrency != null && !lineCurrency.equals(orderCurrency)) {
      throw new CurrencyMismatchException(orderCurrency, "Line currency must match order currency");
    }
  }

  private SalesOrderLine mapLineRequestToEntity(
      SalesOrderLineRequest req, UUID salesOrderId, String orderCurrency) {
    // If unitPrice is provided, its currency MUST match the parent SalesOrder currency
    validateLineCurrency(req.getUnitPrice(), req.getCurrency(), orderCurrency);

    // Note: unitPrice can be null for draft/pending lines
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
        .shippedQty(line.getShippedQty())
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
