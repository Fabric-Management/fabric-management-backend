package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.api.LotCompatibilityRequestPort;
import com.fabricmanagement.production.core.workorder.api.WorkOrderHoldPort;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.LineGreigeCover;
import com.fabricmanagement.sales.orderintake.domain.LinePortionReadiness;
import com.fabricmanagement.sales.orderintake.domain.LineProductCorrection;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceStatus;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.LineGreigeCoverRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.LinePortionReadinessRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.LineProductCorrectionRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityAcceptanceRepository;
import com.fabricmanagement.sales.salesorder.app.CatalogLineValidator;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traced correction of a wrongly chosen product (SOI D8, K18, R19). The product of all of its
 * distributions in the order is replaced together (IK-03) against the versions the user saw. The
 * frontend's one-character confirmation is friction, not authority; this command checks the
 * permission, the order scope and the versions. Acceptances stop covering the lines, held pieces of
 * the old product go back, and running work must first be confirmed stopped (HOLD_REQUIRED).
 */
@Service
@RequiredArgsConstructor
public class ProductCorrectionService {

  static final String RELEASE_REASON = "PRODUCT_CORRECTED";
  private static final Set<OrderStatus> CORRECTABLE =
      EnumSet.of(OrderStatus.DRAFT, OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS);

  private final OrderIntakeAccess access;
  private final SalesOrderLineRepository lines;
  private final LineProductCorrectionRepository corrections;
  private final LineGreigeCoverRepository greigeCovers;
  private final QuantityAcceptanceRepository acceptances;
  private final LinePortionReadinessRepository readiness;
  private final CatalogLineValidator catalogLineValidator;
  private final ProductionOrderPort production;
  private final WorkOrderHoldPort holds;
  private final ConfirmationGate gate;
  private final LotCompatibilityRequestPort compatibilityRequests;
  private final Clock clock;
  private final com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock
      fulfilmentLock;

  @Transactional
  public List<FulfilmentDtos.CorrectionView> correct(
      UUID orderId, FulfilmentDtos.CorrectProduct input, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = access.writableOrder(orderId, actor);
    if (!CORRECTABLE.contains(order.getStatus())) {
      throw OrderIntakeException.conflict(
          "ORDER_NOT_CORRECTABLE",
          "The product cannot be corrected while the order is " + order.getStatus());
    }
    if (Objects.equals(input.fromProductId(), input.toProductId())) {
      throw OrderIntakeException.rule("SAME_PRODUCT", "Choose a different product");
    }
    fulfilmentLock.lockAll(
        tenantId, input.lines().stream().map(FulfilmentDtos.LineVersion::lineId).toList());
    List<SalesOrderLine> all =
        lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId());
    Map<UUID, SalesOrderLine> group =
        all.stream()
            .filter(line -> input.fromProductId().equals(line.getProductId()))
            .collect(Collectors.toMap(SalesOrderLine::getId, Function.identity()));
    Set<UUID> requested =
        input.lines().stream().map(FulfilmentDtos.LineVersion::lineId).collect(Collectors.toSet());
    if (group.isEmpty() || !group.keySet().equals(requested)) {
      throw OrderIntakeException.rule(
          "PRODUCT_GROUP_MISMATCH",
          "Name every distribution of the product in this order; they are corrected together");
    }
    for (FulfilmentDtos.LineVersion ref : input.lines()) {
      SalesOrderLine line = group.get(ref.lineId());
      if (!ref.expectedVersion().equals(line.getVersion())) {
        throw OrderIntakeException.conflict(
            "STALE_VERSION", "The line changed since it was read; reload and decide again");
      }
      if (line.getShippedQty() != null && line.getShippedQty().compareTo(BigDecimal.ZERO) > 0) {
        throw OrderIntakeException.conflict(
            "LINE_ALREADY_SHIPPED", "Shipped goods keep their product; open a new line instead");
      }
      if (production.hasActiveProduction(tenantId, line.getId())
          && !holds.isStoppedFor(tenantId, line.getId())) {
        throw OrderIntakeException.conflict(
            "HOLD_REQUIRED",
            "Work is running for this line; request a hold and wait until production confirms"
                + " the stop");
      }
    }

    Instant now = clock.instant();
    for (FulfilmentDtos.LineVersion ref : input.lines()) {
      SalesOrderLine line = group.get(ref.lineId());
      corrections.save(
          LineProductCorrection.record(
              order.getId(),
              line.getId(),
              line.getProductId(),
              input.toProductId(),
              line.getVersion(),
              input.reason(),
              actor,
              now));
      line.correctProduct(input.toProductId());
    }
    catalogLineValidator.validate(tenantId, order.getTradingPartnerId(), all);
    group.values().forEach(lines::save);
    gate.release(group.keySet(), actor, RELEASE_REASON);
    // The stock choice and every confirmed or requested date belonged to the old product (IK-21).
    for (QuantityAcceptance acceptance :
        acceptances.findByTenantIdAndSalesOrderLineIdInAndStatus(
            tenantId, group.keySet(), QuantityAcceptanceStatus.ACTIVE)) {
      acceptance.withdraw(now);
      acceptances.save(acceptance);
    }
    for (LinePortionReadiness record :
        readiness.findByTenantIdAndSalesOrderLineIdInAndStatusIn(
            tenantId,
            group.keySet(),
            EnumSet.of(
                LinePortionReadiness.Status.REQUESTED, LinePortionReadiness.Status.CONFIRMED))) {
      record.withdraw();
      readiness.save(record);
    }
    for (UUID lineId : group.keySet()) {
      compatibilityRequests.withdrawOpen(tenantId, lineId, actor);
      greigeCovers
          .findFirstByTenantIdAndSalesOrderLineIdAndStatus(
              tenantId, lineId, LineGreigeCover.Status.ACTIVE)
          .ifPresent(
              cover -> {
                cover.withdraw(actor, now);
                greigeCovers.save(cover);
              });
    }
    return historyForOrder(tenantId, order.getId());
  }

  @Transactional(readOnly = true)
  public List<FulfilmentDtos.CorrectionView> history(UUID orderId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    return historyForOrder(TenantContext.requireTenantId(), order.getId());
  }

  private List<FulfilmentDtos.CorrectionView> historyForOrder(UUID tenantId, UUID orderId) {
    return corrections
        .findByTenantIdAndSalesOrderIdOrderByCorrectedAtDescIdDesc(tenantId, orderId)
        .stream()
        .map(
            value ->
                new FulfilmentDtos.CorrectionView(
                    value.getId(),
                    value.getSalesOrderLineId(),
                    value.getOldProductId(),
                    value.getNewProductId(),
                    value.getReason(),
                    value.getCorrectedBy(),
                    value.getCorrectedAt()))
        .toList();
  }
}
