package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceStatus;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeAction;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeReadinessDto;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityAcceptanceRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The order-intake readiness view and the actions the current user may take (SOI D4, IK-11). */
@Service
@RequiredArgsConstructor
public class OrderIntakeReadinessService {

  static final String ORDER_WITH_PLANNING = "ORDER_WITH_PLANNING";

  private final OrderIntakeAccess access;
  private final SalesOrderLineRepository lines;
  private final QuantityAcceptanceRepository acceptances;
  private final ConfirmationGate gate;
  private final PieceAllocationPort allocation;
  private final DeliveryPreferenceService deliveryPreference;
  private final IntakePermissions permissions;
  private final Clock clock;

  @Transactional(readOnly = true)
  public OrderIntakeReadinessDto readiness(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = access.readableOrder(orderId, actor);
    List<SalesOrderLine> orderLines =
        lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId());
    boolean draft = order.getStatus() == OrderStatus.DRAFT;
    List<ConfirmationGate.Block> blocks = draft ? gate.blocks(order, orderLines) : List.of();
    Map<UUID, QuantityAcceptance> active =
        orderLines.isEmpty()
            ? Map.of()
            : acceptances
                .findByTenantIdAndSalesOrderLineIdInAndStatus(
                    tenantId,
                    orderLines.stream().map(SalesOrderLine::getId).toList(),
                    QuantityAcceptanceStatus.ACTIVE)
                .stream()
                .collect(
                    Collectors.toMap(QuantityAcceptance::getSalesOrderLineId, Function.identity()));
    Map<UUID, List<PieceAllocationPort.PieceAllocationView>> allocations =
        allocation.activeForOrder(tenantId, order.getId()).stream()
            .collect(
                Collectors.groupingBy(PieceAllocationPort.PieceAllocationView::salesOrderLineId));

    List<OrderIntakeReadinessDto.Line> lineViews = new ArrayList<>();
    for (SalesOrderLine line : orderLines) {
      QuantityAcceptance acceptance = active.get(line.getId());
      List<PieceAllocationPort.PieceAllocationView> held =
          allocations.getOrDefault(line.getId(), List.of());
      lineViews.add(
          new OrderIntakeReadinessDto.Line(
              line.getId(),
              acceptance == null ? null : acceptance.getBasis(),
              acceptance != null && acceptance.isConditional(),
              acceptance == null ? null : acceptance.getAcceptedQty(),
              held.size(),
              held.isEmpty()
                  ? null
                  : held.stream()
                      .map(PieceAllocationPort.PieceAllocationView::quantity)
                      .reduce(BigDecimal.ZERO, BigDecimal::add),
              held.isEmpty() ? null : held.getFirst().unit(),
              blocks.stream()
                  .filter(block -> line.getId().equals(block.lineId()))
                  .map(OrderIntakeReadinessService::toDto)
                  .toList()));
    }
    List<OrderIntakeReadinessDto.Block> orderBlocks =
        blocks.stream()
            .filter(block -> block.lineId() == null)
            .map(OrderIntakeReadinessService::toDto)
            .toList();
    boolean canWrite = access.canWrite(order, actor);
    List<OrderIntakeReadinessDto.Capability> capabilities =
        capabilities(
            draft,
            order.getFlowStage().locksCommercialContent(),
            canWrite,
            blocks.isEmpty() && !orderLines.isEmpty());
    boolean confirmable =
        capabilities.stream()
            .anyMatch(
                capability ->
                    capability.action() == OrderIntakeAction.CONFIRM_ORDER && capability.allowed());
    return new OrderIntakeReadinessDto(
        order.getId(),
        order.getStatus().name(),
        confirmable,
        deliveryPreference.preferenceOf(order.getId()),
        orderBlocks,
        lineViews,
        capabilities,
        clock.instant());
  }

  /**
   * Content actions change what planning evaluated, so they close while the order is with planning
   * (ORDER_WITH_PLANNING); sales must withdraw it to draft first. Evaluation inputs, attachments,
   * readiness and hold requests stay open: they inform planning rather than change the order.
   */
  private List<OrderIntakeReadinessDto.Capability> capabilities(
      boolean draft, boolean contentLocked, boolean canWrite, boolean nothingBlocks) {
    List<OrderIntakeReadinessDto.Capability> result = new ArrayList<>();
    result.add(draftWrite(OrderIntakeAction.EVALUATE_QUANTITY, draft, false, canWrite));
    result.add(draftWrite(OrderIntakeAction.RECORD_STOCK_CHOICE, draft, contentLocked, canWrite));
    result.add(anyWrite(OrderIntakeAction.RECORD_TONE_ACCEPTANCE, contentLocked, canWrite));
    result.add(draftWrite(OrderIntakeAction.ADD_CUSTOM_REQUEST, draft, contentLocked, canWrite));
    result.add(anyWrite(OrderIntakeAction.RECORD_CUSTOMER_DECISION, contentLocked, canWrite));
    result.add(
        draftWrite(OrderIntakeAction.RESOLVE_CUSTOM_REQUEST, draft, contentLocked, canWrite));
    result.add(anyWrite(OrderIntakeAction.RECORD_PARTIAL_DELIVERY, contentLocked, canWrite));
    result.add(anyWrite(OrderIntakeAction.UPLOAD_ATTACHMENT, false, canWrite));
    result.add(anyWrite(OrderIntakeAction.REQUEST_READINESS_CONFIRMATION, false, canWrite));
    result.add(anyWrite(OrderIntakeAction.CORRECT_PRODUCT, contentLocked, canWrite));
    result.add(anyWrite(OrderIntakeAction.REQUEST_HOLD, false, canWrite));
    List<PermissionKey> confirmKeys = List.of(PermissionKey.SALES_CONFIRM);
    String confirmReason =
        !draft
            ? "ORDER_NOT_DRAFT"
            : contentLocked
                ? ORDER_WITH_PLANNING
                : !canWrite
                    ? "NO_OBJECT_ACCESS"
                    : !permissions.has(PermissionKey.SALES_CONFIRM)
                        ? "PERMISSION_DENIED"
                        : !nothingBlocks ? "BLOCKED" : null;
    result.add(
        new OrderIntakeReadinessDto.Capability(
            OrderIntakeAction.CONFIRM_ORDER, confirmReason == null, confirmReason, confirmKeys));
    return result;
  }

  private OrderIntakeReadinessDto.Capability draftWrite(
      OrderIntakeAction action, boolean draft, boolean contentLocked, boolean canWrite) {
    String reason = !draft ? "ORDER_NOT_DRAFT" : writeReason(contentLocked, canWrite);
    return new OrderIntakeReadinessDto.Capability(
        action, reason == null, reason, List.of(PermissionKey.SALES_WRITE));
  }

  private OrderIntakeReadinessDto.Capability anyWrite(
      OrderIntakeAction action, boolean contentLocked, boolean canWrite) {
    String reason = writeReason(contentLocked, canWrite);
    return new OrderIntakeReadinessDto.Capability(
        action, reason == null, reason, List.of(PermissionKey.SALES_WRITE));
  }

  private String writeReason(boolean contentLocked, boolean canWrite) {
    if (contentLocked) {
      return ORDER_WITH_PLANNING;
    }
    if (!canWrite) {
      return "NO_OBJECT_ACCESS";
    }
    return permissions.has(PermissionKey.SALES_WRITE) ? null : "PERMISSION_DENIED";
  }

  private static OrderIntakeReadinessDto.Block toDto(ConfirmationGate.Block block) {
    return new OrderIntakeReadinessDto.Block(block.code(), block.message(), block.pieceIds());
  }
}
