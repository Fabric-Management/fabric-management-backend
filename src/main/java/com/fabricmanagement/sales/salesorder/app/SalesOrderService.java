package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.orderintake.app.CustomerRequestService;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotals;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
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
  private final RequirementProfileService requirementProfileService;
  private final DomainEventPublisher domainEventPublisher;
  private final DocumentNumberGenerator documentNumberGenerator;
  private final SalesOrderTotalsQuery totalsQuery;
  private final SalesOrderAccessPolicy accessPolicy;
  private final com.fabricmanagement.sales.salesorder.infra.repository
          .OrderCoverActivationRepository
      orderCoverActivationRepository;
  private final OrderCoverEnrolmentService orderCoverEnrolmentService;
  private final OrderIntakeHooks orderIntakeHooks;
  private final CustomerRequestService customerRequestService;
  private final OrderApprovalInvalidator approvalInvalidator;
  private final OrderCreationReplay creationReplay;

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
    UUID tenantId = TenantContext.requireTenantId();
    // ADR-0014 D2: no order exists without its customer, whoever calls this.
    if (request.getPartnerId() == null) {
      throw OrderDomainException.rule(
          "CUSTOMER_REQUIRED", "Choose the customer before the order is saved");
    }

    // Resolve partner ID (handles both new and legacy IDs)
    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, request.getPartnerId());
    // ADR-0014 D10: a repeated create (a retried autosave) returns the order it created; repeats
    // with one key are serialised, and the same key with different content is a conflict.
    String creationFingerprint = null;
    if (request.getIdempotencyKey() != null) {
      creationFingerprint = creationReplay.fingerprint(request);
      Optional<SalesOrder> created =
          creationReplay.claim(tenantId, request.getIdempotencyKey(), creationFingerprint);
      if (created.isPresent()) {
        return findById(created.get().getId(), TenantContext.getCurrentUserId())
            .orElseThrow(
                () -> new OrderDomainException("The order created by this request is not visible"));
      }
    }
    orderIntakeHooks.validateLines(tenantId, tradingPartnerId, request.getLines());
    DeliveryTerms terms =
        DeliveryTerms.of(
            request.getDeliveryTerm(), request.getDeliveryPlace(), request.getIncotermsVersion());

    // Generate order number
    LocalDate effectiveDate =
        request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now();
    String orderNumber = generateOrderNumber(tenantId, effectiveDate);
    // Every line keeps its own agreed currency; reject a broken price before anything is saved.
    if (request.getLines() != null) {
      request
          .getLines()
          .forEach(
              line ->
                  SalesOrderLine.validatePricing(
                      line.getRequestedQty(),
                      line.getCurrency(),
                      line.getUnitPrice(),
                      line.getDiscountAmount(),
                      line.getTaxAmount()));
      request
          .getLines()
          .forEach(
              line ->
                  SalesOrderLine.validateTolerance(
                      line.getToleranceUpPct(), line.getToleranceDownPct()));
    }

    orderCoverActivationRepository.lockForOrderInsert(tenantId);
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(tradingPartnerId)
            .orderNumber(orderNumber)
            .customerReference(request.getCustomerReference())
            .orderType(request.getOrderType())
            .orderDate(request.getOrderDate())
            .creationKey(request.getIdempotencyKey())
            .creationRequestHash(creationFingerprint)
            .paymentTerms(request.getPaymentTerms())
            .contactName(blankToNull(request.getContactName()))
            .contactEmail(blankToNull(request.getContactEmail()))
            .contactPhone(blankToNull(request.getContactPhone()))
            .contactWhatsapp(
                contactWhatsapp(request.getContactPhone(), request.getContactWhatsapp()))
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
    order.applyDeliveryTerms(terms);
    order.applyDeliveryTermStatus(
        request.getDeliveryTermStatus(), request.getDeliveryContractReference());
    order.applyAgreementContext(request.getAgreementContext(), request.getAgreementContextNote());
    order.initialiseRequestedDate(request.getRequestedDeliveryDate());

    SalesOrder saved = orderRepository.save(order);

    // Persist embedded lines (validated + moduleSpecs checked)
    List<SalesOrderLine> savedLines = List.of();
    if (request.getLines() != null && !request.getLines().isEmpty()) {
      List<SalesOrderLine> lines =
          request.getLines().stream()
              .map(
                  lineReq -> {
                    moduleSpecsValidator.validate(lineReq);
                    return mapLineRequestToEntity(lineReq, saved.getId());
                  })
              .toList();
      savedLines = lineRepository.saveAll(lines);
      for (int index = 0; index < savedLines.size(); index++) {
        SalesOrderLineRequest lineRequest = request.getLines().get(index);
        if (lineRequest.getRequirementProfile() != null) {
          requirementProfileService.apply(
              savedLines.get(index),
              lineRequest.getRequirementProfile(),
              lineRequest.getModuleSpecs());
        }
      }
    }

    // Joining the create transaction prevents an order being left behind if a request is invalid.
    if (request.getCustomRequests() != null) {
      for (var customRequest : request.getCustomRequests()) {
        customerRequestService.create(
            saved.getId(), customRequest, TenantContext.getCurrentUserId());
      }
    }

    // Get partner details for response
    TradingPartnerDto partner = partnerService.findById(tenantId, tradingPartnerId).orElse(null);

    // The two-arg SalesOrderDto.from(...) substitutes an empty line list. Creation must echo the
    // lines it just persisted, as findById does — callers chain off the response.
    List<SalesOrderLineResponse> lineResponses =
        savedLines.stream().map(this::mapLineToResponse).toList();

    log.info(
        "Sales order created: uid={}, partner={}, lines={}",
        saved.getUid(),
        tradingPartnerId,
        lineResponses.size());
    return SalesOrderDto.from(saved, partner, lineResponses, OrderCurrencyTotals.of(savedLines));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // RULES SHARED WITH THE SAFE EDIT
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * A profiled line whose product or module type changes needs a new profile from a new basis; the
   * safe edit (CEDIT-03) applies this rule.
   */
  static void assertProfileContextChange(
      SalesOrderLine existing,
      java.util.UUID requestedProductId,
      ModuleType requestedModuleType,
      com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput
          requestedProfile) {
    var current = existing.getRequirementProfileSnapshot();
    if (current == null) {
      return;
    }
    boolean contextChanged =
        !Objects.equals(existing.getProductId(), requestedProductId)
            || existing.getModuleType() != requestedModuleType;
    if (!contextChanged) {
      return;
    }
    if (requestedProfile == null) {
      throw new OrderDomainException(
          "Changing a profiled line's product or module type requires a new requirement profile basis");
    }
    if (current.basis().equals(requestedProfile.basis())) {
      throw new OrderDomainException(
          "Changing a profiled line's product or module type requires full re-resolution from a new basis");
    }
  }

  /** A line's shipment preference as requested; omitted means "as ready" (LINE-PREFERENCES-1). */
  static LineShipmentPreference shipmentPreferenceOrDefault(LineShipmentPreference requested) {
    return requested == null ? LineShipmentPreference.AS_READY : requested;
  }

  static String normaliseWidthUnit(String unit) {
    return unit == null || unit.isBlank() ? null : unit.trim().toUpperCase(java.util.Locale.ROOT);
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
  public Optional<SalesOrderDto> findById(UUID orderId, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    return orderRepository
        .findOne(accessPolicy.readRestriction(tenantId, currentUserId).and(byId(orderId)))
        .map(
            order -> {
              TradingPartnerDto partner =
                  partnerService.findById(tenantId, order.getTradingPartnerId()).orElse(null);
              // Load embedded lines for detail view
              List<SalesOrderLine> lines =
                  lineRepository
                      .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
                          tenantId, order.getId());
              return SalesOrderDto.from(
                  order,
                  partner,
                  lines.stream().map(this::mapLineToResponse).toList(),
                  OrderCurrencyTotals.of(lines));
            });
  }

  /**
   * Find order by order number.
   *
   * @param orderNumber Order number
   * @return Order DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<SalesOrderDto> findByOrderNumber(String orderNumber, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    return orderRepository
        .findOne(
            accessPolicy.readRestriction(tenantId, currentUserId).and(byOrderNumber(orderNumber)))
        .map(this::summary);
  }

  /**
   * Find orders by partner ID.
   *
   * @param partnerId Partner ID (can be TradingPartner.id or legacy Company.id)
   * @return List of orders
   */
  @Transactional(readOnly = true)
  public List<SalesOrderDto> findByPartner(UUID partnerId, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    // Resolve partner ID to ensure we query with the correct ID
    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, partnerId);

    Specification<SalesOrder> restriction =
        accessPolicy
            .readRestriction(tenantId, currentUserId)
            .and(active())
            .and(byPartner(tradingPartnerId));
    return orderRepository.findAll(restriction, Sort.by(Sort.Direction.DESC, "orderDate")).stream()
        .collect(Collectors.collectingAndThen(Collectors.toList(), this::summaries));
  }

  /**
   * Find orders by status.
   *
   * @param status Order status
   * @return List of orders
   */
  @Transactional(readOnly = true)
  public List<SalesOrderDto> findByStatus(OrderStatus status, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    return orderRepository
        .findAll(accessPolicy.readRestriction(tenantId, currentUserId).and(byStatus(status)))
        .stream()
        .collect(Collectors.collectingAndThen(Collectors.toList(), this::summaries));
  }

  /**
   * Find open orders (not in terminal status).
   *
   * @return List of open orders
   */
  @Transactional(readOnly = true)
  public List<SalesOrderDto> findOpenOrders(UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    Specification<SalesOrder> restriction =
        accessPolicy.readRestriction(tenantId, currentUserId).and(active()).and(open());
    return orderRepository.findAll(restriction, Sort.by(Sort.Direction.DESC, "orderDate")).stream()
        .collect(Collectors.collectingAndThen(Collectors.toList(), this::summaries));
  }

  /**
   * Find overdue orders.
   *
   * @return List of overdue orders
   */
  @Transactional(readOnly = true)
  public List<SalesOrderDto> findOverdueOrders(UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    Specification<SalesOrder> restriction =
        accessPolicy
            .readRestriction(tenantId, currentUserId)
            .and(active())
            .and(overdue(LocalDate.now()));
    return orderRepository.findAll(restriction, Sort.by(Sort.Direction.ASC, "committedOn")).stream()
        .collect(Collectors.collectingAndThen(Collectors.toList(), this::summaries));
  }

  /**
   * Get all orders with pagination.
   *
   * @param pageable Pagination info
   * @return Page of orders
   */
  @Transactional(readOnly = true)
  public Page<SalesOrderDto> findAll(Pageable pageable, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    Page<SalesOrder> page =
        orderRepository.findAll(
            accessPolicy.readRestriction(tenantId, currentUserId).and(active()), pageable);
    Map<UUID, OrderCurrencyTotals> totals =
        totalsQuery.forOrders(tenantId, page.map(SalesOrder::getId).getContent());
    return page.map(order -> SalesOrderDto.from(order, totals.get(order.getId())));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * The customer approved the sent version and the flow moved to the customer's approval: the order
   * is confirmed and its accepted pieces are held, in the caller's transaction. Nothing else
   * confirms an order; there is no manual confirmation.
   */
  @Transactional
  public SalesOrderDto confirmApprovedByCustomer(SalesOrder order) {
    order.confirmByCustomer();
    return finalizeConfirmation(order, TenantContext.requireTenantId());
  }

  /**
   * Confirms a seeded order without the planning and customer approval flow, for demo data and test
   * fixtures; no endpoint reaches it. The order-intake conditions still apply.
   */
  @Transactional
  public SalesOrderDto confirmDemoSeedOrder(UUID orderId) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    orderIntakeHooks.checkConfirmable(
        order, lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()));
    order.confirmSeededDemoOrder();
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
  static ModuleType deriveOrderModuleType(List<SalesOrderLine> lines) {
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

  private SalesOrderDto finalizeConfirmation(SalesOrder order, UUID tenantId) {
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order confirmed: uid={}", saved.getUid());

    TradingPartnerDto partner =
        partnerService.findById(tenantId, saved.getTradingPartnerId()).orElse(null);

    List<SalesOrderLine> orderLines =
        lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(saved.getId());

    // SOI D4 (A05): accepted pieces are re-checked and held in this transaction, or nothing is.
    orderIntakeHooks.allocateAtConfirmation(saved, orderLines, TenantContext.getCurrentUserId());

    com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime coverRegime =
        orderCoverEnrolmentService.decide(saved, orderLines);
    orderRepository.save(saved);
    if (coverRegime == com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime.LEGACY) {
      ruleEngine.processConfirmedOrder(saved);
    }

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
            snapshotLines,
            coverRegime));

    List<SalesOrderLineResponse> lineResponses =
        orderLines.stream().map(this::mapLineToResponse).toList();
    return SalesOrderDto.from(saved, partner, lineResponses, totalsOf(saved));
  }

  /**
   * Start processing an order.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto startProcessing(UUID orderId, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    requireWriteAccess(tenantId, currentUserId, order);
    order.startProcessing();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order processing started: uid={}", saved.getUid());
    return summary(saved);
  }

  /**
   * Ship an order.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto shipOrder(UUID orderId, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    requireWriteAccess(tenantId, currentUserId, order);
    order.ship();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order shipped: uid={}", saved.getUid());
    return summary(saved);
  }

  /**
   * Deliver an order.
   *
   * @param orderId Order ID
   * @param deliveryDate Actual delivery date
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto deliverOrder(UUID orderId, UUID currentUserId, LocalDate deliveryDate) {
    UUID tenantId = TenantContext.requireTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    requireWriteAccess(tenantId, currentUserId, order);
    order.deliver(deliveryDate);
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order delivered: uid={}", saved.getUid());
    return summary(saved);
  }

  /**
   * Cancel an order.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto cancelOrder(UUID orderId, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    requireWriteAccess(tenantId, currentUserId, order);

    // Collect active line IDs for cascade notification before cancellation
    List<UUID> activeLineIds =
        lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()).stream()
            .map(SalesOrderLine::getId)
            .toList();

    order.cancel();
    SalesOrder saved = orderRepository.save(order);
    orderIntakeHooks.releaseOnCancellation(activeLineIds, currentUserId);
    // A link out with the customer, or a version waiting for an internal approval, stops counting.
    approvalInvalidator.withdrawOpen(saved.getId(), "The order was cancelled", currentUserId);
    approvalInvalidator.resolveChangeRequests(saved.getId());

    domainEventPublisher.publish(
        new SalesOrderCancelledEvent(
            tenantId, saved.getId(), saved.getOrderNumber(), activeLineIds));

    log.info("Sales order cancelled: uid={}, lineCount={}", saved.getUid(), activeLineIds.size());
    return summary(saved);
  }

  /**
   * Put an order on hold.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto holdOrder(UUID orderId, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    requireWriteAccess(tenantId, currentUserId, order);
    order.hold();
    SalesOrder saved = orderRepository.save(order);

    log.info(
        "Sales order put on hold: uid={}, previousStatus={}",
        saved.getUid(),
        saved.getStatusBeforeHold());
    return summary(saved);
  }

  /**
   * Resume an order from ON_HOLD.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto resumeOrder(UUID orderId, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    requireWriteAccess(tenantId, currentUserId, order);
    order.resume();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order resumed: uid={}, restoredStatus={}", saved.getUid(), saved.getStatus());
    return summary(saved);
  }

  /**
   * Revise a rejected order back to DRAFT.
   *
   * @param orderId Order ID
   * @return Updated order DTO
   */
  @Transactional
  public SalesOrderDto reviseOrder(UUID orderId, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    requireWriteAccess(tenantId, currentUserId, order);
    order.reviseRejected();
    SalesOrder saved = orderRepository.save(order);

    log.info("Sales order revised to DRAFT: uid={}", saved.getUid());
    return summary(saved);
  }

  /**
   * Soft delete an order.
   *
   * @param orderId Order ID
   */
  @Transactional
  public void deleteOrder(UUID orderId, UUID currentUserId) {
    UUID tenantId = TenantContext.requireTenantId();

    SalesOrder order = getOrderOrThrow(tenantId, orderId);
    requireWriteAccess(tenantId, currentUserId, order);
    // Planning may be evaluating it: take it back to the draft first.
    order.assertCommercialContentEditable();

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

  private void requireWriteAccess(UUID tenantId, UUID currentUserId, SalesOrder order) {
    if (!accessPolicy.canWrite(tenantId, currentUserId, order)) {
      throw new AccessDeniedException("You do not have access to update this sales order.");
    }
  }

  private SalesOrder getOrderOrThrow(UUID tenantId, UUID orderId) {
    return orderRepository
        .findByTenantIdAndId(tenantId, orderId)
        .orElseThrow(
            () ->
                new com.fabricmanagement.sales.common.exception.OrderDomainException(
                    "Sales order not found: " + orderId));
  }

  private Specification<SalesOrder> byId(UUID orderId) {
    return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), orderId);
  }

  private Specification<SalesOrder> byOrderNumber(String orderNumber) {
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.equal(root.get("orderNumber"), orderNumber);
  }

  private Specification<SalesOrder> byPartner(UUID partnerId) {
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.equal(root.get("tradingPartnerId"), partnerId);
  }

  private Specification<SalesOrder> byStatus(OrderStatus status) {
    return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
  }

  private Specification<SalesOrder> active() {
    return (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("isActive"));
  }

  private Specification<SalesOrder> open() {
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.not(
            root.get("status").in(List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED)));
  }

  private Specification<SalesOrder> overdue(LocalDate date) {
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.and(
            criteriaBuilder.lessThan(root.<LocalDate>get("committedOn"), date),
            criteriaBuilder.not(
                root.get("status").in(List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED))));
  }

  // ── Line mapping helpers ─────────────────────────────────────────────────

  /**
   * The whole order is checked once with its grand total in every agreed currency. The approval
   * module evaluates each threshold in its policy's currency, converting and summing these amounts,
   * so a policy in one currency also governs orders priced in others.
   */
  /**
   * The agreed tolerance of a line is recorded by whoever saves it, at that moment. Where it was
   * agreed is recorded by the customer's approval of the sent order version.
   */
  private static void recordTolerance(SalesOrderLine line, BigDecimal upPct, BigDecimal downPct) {
    line.recordTolerance(upPct, downPct, TenantContext.getCurrentUserId(), java.time.Instant.now());
  }

  /** WhatsApp only makes sense for a contact with a phone number. */
  private static boolean contactWhatsapp(String phone, Boolean requested) {
    return Boolean.TRUE.equals(requested) && blankToNull(phone) != null;
  }

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private OrderCurrencyTotals totalsOf(SalesOrder order) {
    return OrderCurrencyTotals.of(
        lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()));
  }

  private SalesOrderDto summary(SalesOrder order) {
    return SalesOrderDto.from(order, totalsOf(order));
  }

  private List<SalesOrderDto> summaries(List<SalesOrder> orders) {
    Map<UUID, OrderCurrencyTotals> totals =
        totalsQuery.forOrders(
            TenantContext.requireTenantId(), orders.stream().map(SalesOrder::getId).toList());
    return orders.stream()
        .map(order -> SalesOrderDto.from(order, totals.get(order.getId())))
        .toList();
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

  /** Pricing was validated by {@link #createOrder} before the order was saved. */
  private SalesOrderLine mapLineRequestToEntity(SalesOrderLineRequest req, UUID salesOrderId) {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(salesOrderId)
            .productId(req.getProductId())
            .productDesc(req.getProductDesc())
            .requestedQty(req.getRequestedQty())
            .unit(req.getUnit())
            .currency(req.getCurrency())
            .unitPriceAmount(req.getUnitPrice())
            .discountAmountValue(req.getDiscountAmount())
            .taxAmountValue(req.getTaxAmount())
            .moduleType(req.getModuleType())
            .moduleSpecs(req.getModuleSpecs())
            .colorId(req.getColorId())
            .finishedWidth(req.getFinishedWidth())
            .finishedWidthUnit(normaliseWidthUnit(req.getFinishedWidthUnit()))
            .requestedDeliveryDate(req.getRequestedDeliveryDate())
            .singleLotRequired(Boolean.TRUE.equals(req.getSingleLotRequired()))
            .shipmentPreference(shipmentPreferenceOrDefault(req.getShipmentPreference()))
            .lineStatus(SalesOrderLineStatus.PENDING)
            .build();
    recordTolerance(line, req.getToleranceUpPct(), req.getToleranceDownPct());
    return line;
  }

  private SalesOrderLineResponse mapLineToResponse(SalesOrderLine line) {
    return lineResponse(line);
  }

  /** A line as the order detail shows it; the safe edit's bases use the same mapping. */
  static SalesOrderLineResponse lineResponse(SalesOrderLine line) {
    return SalesOrderLineResponse.builder()
        .id(line.getId())
        .uid(line.getUid())
        .salesOrderId(line.getSalesOrderId())
        .productId(line.getProductId())
        .productDesc(line.getProductDesc())
        .colorId(line.getColorId())
        .finishedWidth(line.getFinishedWidth())
        .finishedWidthUnit(line.getFinishedWidthUnit())
        .requestedDeliveryDate(line.getRequestedDeliveryDate())
        .initialRequestedQty(line.getInitialRequestedQty())
        .singleLotRequired(line.isSingleLotRequired())
        .shipmentPreference(line.getShipmentPreference())
        .requestedQty(line.getRequestedQty())
        .shippedQty(line.getShippedQty())
        .unit(line.getUnit())
        // Agreed amounts as stored (unit price keeps 4 decimals); never re-rounded on the way out,
        // or an edit would save a different price back.
        .unitPrice(line.getUnitPriceAmount())
        .currency(line.getCurrency())
        .discountAmount(line.getDiscountAmountValue())
        .taxAmount(line.getTaxAmountValue())
        .toleranceUpPct(line.getToleranceUpPct())
        .toleranceDownPct(line.getToleranceDownPct())
        .toleranceRecordedBy(line.getToleranceRecordedBy())
        .toleranceRecordedAt(line.getToleranceRecordedAt())
        .moduleType(line.getModuleType())
        .moduleSpecs(line.getModuleSpecs())
        .requirementProfile(line.getRequirementProfileSnapshot())
        .lineStatus(line.getLineStatus())
        .recipeId(line.getRecipeId())
        .version(line.getVersion())
        .build();
  }

  private String generateOrderNumber(UUID tenantId, LocalDate orderDate) {
    return documentNumberGenerator.generate(tenantId, "SALES_ORDER", "SO", orderDate, 5);
  }
}
