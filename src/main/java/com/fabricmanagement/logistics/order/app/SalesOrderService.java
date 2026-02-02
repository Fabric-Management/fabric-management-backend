package com.fabricmanagement.logistics.order.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.app.TradingPartnerResolver;
import com.fabricmanagement.common.platform.company.app.TradingPartnerService;
import com.fabricmanagement.common.platform.company.dto.TradingPartnerDto;
import com.fabricmanagement.logistics.order.domain.OrderStatus;
import com.fabricmanagement.logistics.order.domain.SalesOrder;
import com.fabricmanagement.logistics.order.dto.CreateSalesOrderRequest;
import com.fabricmanagement.logistics.order.dto.SalesOrderDto;
import com.fabricmanagement.logistics.order.infra.repository.SalesOrderRepository;
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

  private static final DateTimeFormatter ORDER_NUMBER_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd");

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
    String orderNumber = generateOrderNumber(tenantId, request.getOrderDate());

    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(tradingPartnerId)
            .orderNumber(orderNumber)
            .customerReference(request.getCustomerReference())
            .orderType(request.getOrderType())
            .orderDate(request.getOrderDate())
            .requestedDeliveryDate(request.getRequestedDeliveryDate())
            .promisedDeliveryDate(request.getPromisedDeliveryDate())
            .totalAmount(request.getTotalAmount())
            .taxAmount(request.getTaxAmount())
            .discountAmount(request.getDiscountAmount())
            .currency(request.getCurrency())
            .shippingAddress(request.getShippingAddress())
            .billingAddress(request.getBillingAddress())
            .shippingMethod(request.getShippingMethod())
            .notes(request.getNotes())
            .metadata(request.getMetadata())
            .build();

    SalesOrder saved = orderRepository.save(order);

    // Get partner details for response
    TradingPartnerDto partner = partnerService.findById(tenantId, tradingPartnerId).orElse(null);

    log.info("Sales order created: uid={}, partner={}", saved.getUid(), tradingPartnerId);
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
              return SalesOrderDto.from(order, partner);
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
    return SalesOrderDto.from(saved);
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
        .orElseThrow(() -> new IllegalArgumentException("Sales order not found: " + orderId));
  }

  private String generateOrderNumber(UUID tenantId, LocalDate orderDate) {
    String prefix = "SO-" + orderDate.format(ORDER_NUMBER_DATE_FORMAT) + "-";
    String maxNumber =
        orderRepository.findMaxOrderNumber(tenantId, prefix).orElse(prefix + "00000");

    // Extract sequence number and increment
    String sequencePart = maxNumber.substring(prefix.length());
    int sequence = Integer.parseInt(sequencePart) + 1;

    return prefix + String.format("%05d", sequence);
  }
}
