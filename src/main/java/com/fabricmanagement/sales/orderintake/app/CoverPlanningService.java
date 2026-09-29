package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.workorder.api.query.ProductionHistoryQueryService;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.CoverPortionKind;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestStatus;
import com.fabricmanagement.sales.orderintake.domain.LineGreigeCover;
import com.fabricmanagement.sales.orderintake.domain.LinePortionReadiness;
import com.fabricmanagement.sales.orderintake.domain.OrderArrivalEstimate;
import com.fabricmanagement.sales.orderintake.domain.PartialDeliveryPreference;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.LineGreigeCoverRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.LinePortionReadinessRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.OrderArrivalEstimateRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.port.LineStockPortionPort;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mixed cover and readiness of order lines (SOI D5, D6). Portions of a line's open quantity:
 * finished stock held at confirmation, finished goods planning confirmed from greige, and the new
 * supply that remains. Their sum is the open quantity, never more (R12). Readiness dates are
 * confirmed per portion by planning or the warehouse; the arrival at the customer comes from a
 * carrier or an authorised, sourced record (A07, A07-b). Nothing here is a fixed lead time.
 */
@Service
@RequiredArgsConstructor
public class CoverPlanningService {

  private static final Set<LinePortionReadiness.Status> OPEN =
      EnumSet.of(LinePortionReadiness.Status.REQUESTED, LinePortionReadiness.Status.CONFIRMED);
  private static final Set<OrderStatus> CLOSED =
      EnumSet.of(OrderStatus.CANCELLED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

  private final OrderIntakeAccess access;
  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final LineGreigeCoverRepository greigeCovers;
  private final LinePortionReadinessRepository readiness;
  private final OrderArrivalEstimateRepository arrivals;
  private final LineStockPortionPort stockPortion;
  private final ProductionHistoryQueryService history;
  private final DeliveryPreferenceService deliveryPreference;
  private final CustomerProductRequestRepository customRequests;
  private final Clock clock;

  /** Planning or the dyehouse confirms what part of the line is made from available greige. */
  @Transactional
  public FulfilmentDtos.LineOutlook confirmGreigeCover(
      UUID lineId, FulfilmentDtos.ConfirmGreigeCover input, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrderLine line = activeLine(tenantId, lineId);
    SalesOrder order = openOrder(tenantId, line.getSalesOrderId());
    BigDecimal stock =
        stockPortion
            .ownFinishedStock(line)
            .orElseThrow(
                () ->
                    OrderIntakeException.conflict(
                        "COVER_PORTION_UNKNOWN",
                        "The finished stock held for the line cannot be stated in its unit"));
    BigDecimal room = open(line).subtract(stock);
    if (input.finishedQuantity().compareTo(room) > 0) {
      throw OrderIntakeException.rule(
          "GREIGE_EXCEEDS_OPEN",
          "Finished goods from greige cannot exceed the open quantity less held stock: " + room);
    }
    ProductionHistoryQueryService.YieldHistory yield =
        history.yieldHistory(tenantId, line.getProductId());
    greigeCovers
        .findFirstByTenantIdAndSalesOrderLineIdAndStatus(
            tenantId, lineId, LineGreigeCover.Status.ACTIVE)
        .ifPresent(
            previous -> {
              previous.withdraw(actor, clock.instant());
              greigeCovers.save(previous);
            });
    greigeCovers.save(
        LineGreigeCover.confirm(
            order.getId(),
            lineId,
            input.finishedQuantity(),
            line.getUnit(),
            input.greigeBatchIds(),
            input.basisNote(),
            yield.sampleSize(),
            yield.averagePercent(),
            actor,
            clock.instant()));
    return lineOutlook(tenantId, line);
  }

  @Transactional
  public FulfilmentDtos.LineOutlook withdrawGreigeCover(UUID lineId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrderLine line = activeLine(tenantId, lineId);
    LineGreigeCover cover =
        greigeCovers
            .findFirstByTenantIdAndSalesOrderLineIdAndStatus(
                tenantId, lineId, LineGreigeCover.Status.ACTIVE)
            .orElseThrow(() -> OrderIntakeException.notFound("Greige cover of line", lineId));
    cover.withdraw(actor, clock.instant());
    greigeCovers.save(cover);
    return lineOutlook(tenantId, line);
  }

  /** Sales asks planning (production portions) or the warehouse (stock) for a readiness date. */
  @Transactional
  public FulfilmentDtos.LineOutlook requestReadiness(
      UUID orderId, UUID lineId, CoverPortionKind portion, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = access.writableOrder(orderId, actor);
    if (CLOSED.contains(order.getStatus())) {
      throw OrderIntakeException.conflict("ORDER_CLOSED", "The order is " + order.getStatus());
    }
    SalesOrderLine line = access.line(order, lineId);
    Optional<BigDecimal> quantity = portionQuantities(tenantId, line).get(portion);
    if (quantity != null && quantity.isPresent() && quantity.get().signum() <= 0) {
      throw OrderIntakeException.rule(
          "PORTION_EMPTY", "Nothing of this line is covered from " + portion);
    }
    if (readiness
        .findFirstByTenantIdAndSalesOrderLineIdAndPortionAndStatusIn(
            tenantId, lineId, portion, OPEN)
        .isEmpty()) {
      readiness.save(
          LinePortionReadiness.request(order.getId(), lineId, portion, actor, clock.instant()));
    }
    return lineOutlook(tenantId, line);
  }

  /** Planning confirms when a production portion will be ready (A07). */
  @Transactional
  public FulfilmentDtos.LineOutlook confirmProductionReadiness(
      UUID lineId, CoverPortionKind portion, FulfilmentDtos.ConfirmReadiness input, UUID actor) {
    if (portion == CoverPortionKind.FINISHED_STOCK) {
      throw OrderIntakeException.rule(
          "WAREHOUSE_CONFIRMS_STOCK", "Stock readiness is confirmed by the warehouse");
    }
    return confirm(lineId, portion, input, actor);
  }

  /** The warehouse confirms when the held stock is ready to ship (A07-b). */
  @Transactional
  public FulfilmentDtos.LineOutlook confirmShipReadiness(
      UUID lineId, FulfilmentDtos.ConfirmReadiness input, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrderLine line = activeLine(tenantId, lineId);
    boolean held =
        stockPortion.ownFinishedStock(line).map(value -> value.signum() > 0).orElse(false);
    if (!held) {
      throw OrderIntakeException.rule("NOTHING_HELD", "No stock is held for this line");
    }
    return confirm(lineId, CoverPortionKind.FINISHED_STOCK, input, actor);
  }

  @Transactional(readOnly = true)
  public List<FulfilmentDtos.ReadinessRequestView> openRequests(boolean warehouse) {
    Set<CoverPortionKind> portions =
        warehouse
            ? EnumSet.of(CoverPortionKind.FINISHED_STOCK)
            : EnumSet.of(CoverPortionKind.FROM_GREIGE, CoverPortionKind.NEW_SUPPLY);
    return readiness
        .findByTenantIdAndStatusAndPortionInOrderByRequestedAtAscIdAsc(
            TenantContext.requireTenantId(), LinePortionReadiness.Status.REQUESTED, portions)
        .stream()
        .map(
            value ->
                new FulfilmentDtos.ReadinessRequestView(
                    value.getId(),
                    value.getSalesOrderId(),
                    value.getSalesOrderLineId(),
                    value.getPortion(),
                    value.getRequestedBy(),
                    value.getRequestedAt()))
        .toList();
  }

  /** Records the arrival estimate from a carrier or an authorised, sourced record (A07-b). */
  @Transactional
  public FulfilmentDtos.ArrivalView recordArrival(
      UUID orderId, FulfilmentDtos.RecordArrivalEstimate input, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order =
        orders
            .findByTenantIdAndId(tenantId, orderId)
            .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
            .orElseThrow(() -> OrderIntakeException.notFound("Sales order", orderId));
    arrivals
        .findFirstByTenantIdAndSalesOrderIdAndSupersededAtIsNull(tenantId, order.getId())
        .ifPresent(
            previous -> {
              previous.supersede(clock.instant());
              arrivals.save(previous);
            });
    OrderArrivalEstimate saved =
        arrivals.save(
            OrderArrivalEstimate.record(
                order.getId(),
                input.earliestOn(),
                input.latestOn(),
                input.source(),
                input.sourceReference(),
                actor,
                clock.instant()));
    return arrivalView(saved);
  }

  @Transactional(readOnly = true)
  public FulfilmentDtos.DeliveryOutlook outlook(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = access.readableOrder(orderId, actor);
    List<FulfilmentDtos.LineOutlook> lineViews = new ArrayList<>();
    for (SalesOrderLine line :
        lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId())) {
      lineViews.add(lineOutlook(tenantId, line));
    }
    PartialDeliveryPreference preference = deliveryPreference.preferenceOf(order.getId());
    Set<CustomerRequestStatus> finished =
        EnumSet.of(CustomerRequestStatus.RESOLVED, CustomerRequestStatus.CLOSED);
    List<UUID> pending =
        java.util.stream.Stream.concat(
                customRequests
                    .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
                        tenantId, order.getId())
                    .stream()
                    .filter(request -> !request.getStatus().isFinished()),
                customRequests
                    .findByTenantIdAndOriginOrderIdAndStatusNotInAndIsActiveTrue(
                        tenantId, order.getId(), finished)
                    .stream())
            .map(CustomerProductRequest::getId)
            .distinct()
            .toList();
    return new FulfilmentDtos.DeliveryOutlook(
        order.getId(),
        lineViews,
        arrivals
            .findFirstByTenantIdAndSalesOrderIdAndSupersededAtIsNull(tenantId, order.getId())
            .map(CoverPlanningService::arrivalView)
            .orElse(null),
        preference,
        preference == PartialDeliveryPreference.ALLOWED,
        pending);
  }

  private FulfilmentDtos.LineOutlook confirm(
      UUID lineId, CoverPortionKind portion, FulfilmentDtos.ConfirmReadiness input, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrderLine line = activeLine(tenantId, lineId);
    openOrder(tenantId, line.getSalesOrderId());
    Map<CoverPortionKind, Optional<BigDecimal>> quantities = portionQuantities(tenantId, line);
    if (exceedsOpen(quantities)) {
      throw OrderIntakeException.conflict(
          "COVER_EXCEEDS_OPEN",
          "Held stock and greige cover exceed the outstanding quantity; revise the cover first");
    }
    BigDecimal quantity =
        quantities
            .get(portion)
            .orElseThrow(
                () ->
                    OrderIntakeException.conflict(
                        "COVER_PORTION_UNKNOWN", "The portion quantity is unknown"));
    if (quantity.signum() <= 0) {
      throw OrderIntakeException.rule(
          "PORTION_EMPTY", "There is nothing to confirm for this portion");
    }
    LinePortionReadiness record =
        readiness
            .findFirstByTenantIdAndSalesOrderLineIdAndPortionAndStatusIn(
                tenantId, lineId, portion, OPEN)
            .orElseGet(
                () -> LinePortionReadiness.unrequested(line.getSalesOrderId(), lineId, portion));
    record.confirm(input.readyOn(), input.basisNote(), actor, clock.instant());
    readiness.save(record);
    return lineOutlook(tenantId, line);
  }

  private FulfilmentDtos.LineOutlook lineOutlook(UUID tenantId, SalesOrderLine line) {
    Map<CoverPortionKind, Optional<BigDecimal>> quantities = portionQuantities(tenantId, line);
    Map<CoverPortionKind, LinePortionReadiness> records =
        readiness
            .findByTenantIdAndSalesOrderLineIdInAndStatusIn(tenantId, List.of(line.getId()), OPEN)
            .stream()
            .collect(
                Collectors.toMap(
                    LinePortionReadiness::getPortion, Function.identity(), (left, right) -> left));
    boolean greigeConfirmed =
        greigeCovers
            .findFirstByTenantIdAndSalesOrderLineIdAndStatus(
                tenantId, line.getId(), LineGreigeCover.Status.ACTIVE)
            .isPresent();
    List<FulfilmentDtos.PortionOutlook> portions = new ArrayList<>();
    LocalDate latest = null;
    boolean allConfirmed = !exceedsOpen(quantities);
    for (CoverPortionKind kind : CoverPortionKind.values()) {
      Optional<BigDecimal> quantity = quantities.get(kind);
      BigDecimal value = quantity.orElse(null);
      if (value != null && value.signum() <= 0) {
        continue;
      }
      LinePortionReadiness record = records.get(kind);
      boolean confirmed =
          record != null && record.getStatus() == LinePortionReadiness.Status.CONFIRMED;
      allConfirmed &= confirmed && value != null;
      if (confirmed && (latest == null || record.getReadyOn().isAfter(latest))) {
        latest = record.getReadyOn();
      }
      portions.add(
          new FulfilmentDtos.PortionOutlook(
              kind,
              value,
              value != null
                  && (kind == CoverPortionKind.FINISHED_STOCK
                      || (kind == CoverPortionKind.FROM_GREIGE && greigeConfirmed)),
              record == null ? null : record.getStatus(),
              record == null ? null : record.getReadyOn(),
              record == null ? null : record.getBasisNote(),
              record == null ? null : record.getConfirmedBy(),
              record == null ? null : record.getConfirmedAt()));
    }
    ProductionHistoryQueryService.YieldHistory yield =
        history.yieldHistory(tenantId, line.getProductId());
    ProductionHistoryQueryService.LeadTimeHistory lead =
        history.leadTimeHistory(tenantId, line.getProductId());
    return new FulfilmentDtos.LineOutlook(
        line.getId(),
        open(line),
        line.getUnit(),
        portions,
        allConfirmed && !portions.isEmpty() ? latest : null,
        exceedsOpen(quantities) ? "COVER_EXCEEDS_OPEN" : null,
        new FulfilmentDtos.HistoryEstimate(
            Math.max(yield.sampleSize(), lead.sampleSize()),
            yield.averagePercent(),
            yield.minPercent(),
            yield.maxPercent(),
            lead.minDays(),
            lead.medianDays(),
            lead.maxDays(),
            lead.basisAt()));
  }

  /** FINISHED_STOCK, FROM_GREIGE and NEW_SUPPLY in the line unit; empty = cannot be stated. */
  private Map<CoverPortionKind, Optional<BigDecimal>> portionQuantities(
      UUID tenantId, SalesOrderLine line) {
    Optional<BigDecimal> stock = stockPortion.ownFinishedStock(line);
    BigDecimal greige =
        greigeCovers
            .findFirstByTenantIdAndSalesOrderLineIdAndStatus(
                tenantId, line.getId(), LineGreigeCover.Status.ACTIVE)
            .map(LineGreigeCover::getFinishedQty)
            .orElse(BigDecimal.ZERO);
    Optional<BigDecimal> newSupply = stock.map(held -> open(line).subtract(held).subtract(greige));
    return Map.of(
        CoverPortionKind.FINISHED_STOCK, stock,
        CoverPortionKind.FROM_GREIGE, Optional.of(greige),
        CoverPortionKind.NEW_SUPPLY, newSupply);
  }

  private static boolean exceedsOpen(Map<CoverPortionKind, Optional<BigDecimal>> quantities) {
    return quantities
        .get(CoverPortionKind.NEW_SUPPLY)
        .map(value -> value.signum() < 0)
        .orElse(false);
  }

  private SalesOrderLine activeLine(UUID tenantId, UUID lineId) {
    return lines
        .findByTenantIdAndId(tenantId, lineId)
        .filter(line -> Boolean.TRUE.equals(line.getIsActive()))
        .orElseThrow(() -> OrderIntakeException.notFound("Sales-order line", lineId));
  }

  private SalesOrder openOrder(UUID tenantId, UUID orderId) {
    SalesOrder order =
        orders
            .findByTenantIdAndId(tenantId, orderId)
            .orElseThrow(() -> OrderIntakeException.notFound("Sales order", orderId));
    if (CLOSED.contains(order.getStatus())) {
      throw OrderIntakeException.conflict("ORDER_CLOSED", "The order is " + order.getStatus());
    }
    return order;
  }

  private static BigDecimal open(SalesOrderLine line) {
    return line.getRemainingQty().max(BigDecimal.ZERO);
  }

  private static FulfilmentDtos.ArrivalView arrivalView(OrderArrivalEstimate value) {
    return new FulfilmentDtos.ArrivalView(
        value.getEarliestOn(),
        value.getLatestOn(),
        value.getSource(),
        value.getSourceReference(),
        value.getRecordedBy(),
        value.getRecordedAt());
  }
}
