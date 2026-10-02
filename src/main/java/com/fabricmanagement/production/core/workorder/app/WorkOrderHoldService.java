package com.fabricmanagement.production.core.workorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.core.workorder.api.WorkOrderHoldPort;
import com.fabricmanagement.production.core.workorder.domain.WorkOrder;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderHold;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.core.workorder.domain.exception.WorkOrderHoldException;
import com.fabricmanagement.production.core.workorder.dto.WorkOrderHoldDtos;
import com.fabricmanagement.production.core.workorder.infra.repository.WorkOrderHoldRepository;
import com.fabricmanagement.production.core.workorder.infra.repository.WorkOrderRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds on running work (SOI D8, R20, A12). The work order carries no owner field, so the tenant
 * designates who may confirm and resume through {@code production:write} (IK-10).
 */
@Service
@RequiredArgsConstructor
public class WorkOrderHoldService implements WorkOrderHoldPort {

  /** Work that exists on the floor or is about to: a hold is meaningful for these. */
  static final Set<WorkOrderStatus> RUNNING =
      EnumSet.of(WorkOrderStatus.APPROVED, WorkOrderStatus.SENT, WorkOrderStatus.IN_PROGRESS);

  private final WorkOrderRepository workOrders;
  private final WorkOrderHoldRepository holds;
  private final Clock clock;
  private final com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock
      fulfilmentLock;
  private final WorkOrderExecutionGuard executionGuard;

  @Override
  @Transactional
  public List<HoldView> request(
      UUID tenantId, UUID salesOrderId, UUID salesOrderLineId, String reason, UUID requestedBy) {
    fulfilmentLock.lock(tenantId, salesOrderLineId);
    List<WorkOrder> running = running(tenantId, salesOrderLineId);
    if (running.isEmpty()) {
      throw WorkOrderHoldException.notRunning();
    }
    boolean open =
        holds
            .findByTenantIdAndSalesOrderLineIdOrderByRequestedAtDescIdDesc(
                tenantId, salesOrderLineId)
            .stream()
            .anyMatch(WorkOrderHold::isOpen);
    if (open) {
      throw WorkOrderHoldException.alreadyRequested();
    }
    List<WorkOrderHold> created = new ArrayList<>();
    for (WorkOrder workOrder : running) {
      WorkOrderHold hold =
          WorkOrderHold.request(
              workOrder.getId(),
              salesOrderId,
              salesOrderLineId,
              reason,
              requestedBy,
              clock.instant());
      hold.setTenantId(tenantId);
      created.add(holds.save(hold));
    }
    Map<UUID, WorkOrder> byId = byId(running);
    return created.stream().map(hold -> view(hold, byId)).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<HoldView> forLine(UUID tenantId, UUID salesOrderLineId) {
    List<WorkOrderHold> lineHolds =
        holds.findByTenantIdAndSalesOrderLineIdOrderByRequestedAtDescIdDesc(
            tenantId, salesOrderLineId);
    Map<UUID, WorkOrder> byId =
        byId(
            workOrders.findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
                tenantId, salesOrderLineId));
    return lineHolds.stream().map(hold -> view(hold, byId)).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isStoppedFor(UUID tenantId, UUID salesOrderLineId) {
    List<WorkOrder> running = running(tenantId, salesOrderLineId);
    if (running.isEmpty()) {
      return true;
    }
    Set<UUID> stopped =
        holds
            .findByTenantIdAndSalesOrderLineIdOrderByRequestedAtDescIdDesc(
                tenantId, salesOrderLineId)
            .stream()
            .filter(hold -> hold.getStatus() == WorkOrderHold.Status.HOLD_CONFIRMED)
            .map(WorkOrderHold::getWorkOrderId)
            .collect(Collectors.toSet());
    return running.stream().allMatch(workOrder -> stopped.contains(workOrder.getId()));
  }

  @Transactional(readOnly = true)
  public List<WorkOrderHoldDtos.HoldDto> open() {
    UUID tenantId = TenantContext.requireTenantId();
    return holds
        .findByTenantIdAndStatusInOrderByRequestedAtAscIdAsc(
            tenantId,
            EnumSet.of(WorkOrderHold.Status.HOLD_REQUESTED, WorkOrderHold.Status.HOLD_CONFIRMED))
        .stream()
        .map(WorkOrderHoldService::toDto)
        .toList();
  }

  @Transactional
  public WorkOrderHoldDtos.HoldDto confirmStop(UUID holdId, WorkOrderHoldDtos.ConfirmStop input) {
    WorkOrderHold hold = load(holdId);
    fulfilmentLock.lock(hold.getTenantId(), hold.getSalesOrderLineId());
    hold.confirmStop(input.stopNote(), actor(), clock.instant());
    return toDto(holds.save(hold));
  }

  @Transactional
  public WorkOrderHoldDtos.HoldDto resume(UUID holdId, WorkOrderHoldDtos.Resume input) {
    WorkOrderHold hold = load(holdId);
    WorkOrder workOrder =
        workOrders
            .findByIdAndTenantIdAndIsActiveTrue(hold.getWorkOrderId(), hold.getTenantId())
            .orElseThrow(
                () -> new NotFoundException("Work order not found: " + hold.getWorkOrderId()));
    executionGuard.requireCurrentProduct(workOrder, workOrder.getOutputProductId());
    hold.resume(
        input.customerChangeSettled(),
        input.checksCompleted(),
        input.note(),
        actor(),
        clock.instant());
    return toDto(holds.save(hold));
  }

  private List<WorkOrder> running(UUID tenantId, UUID salesOrderLineId) {
    return workOrders
        .findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
            tenantId, salesOrderLineId)
        .stream()
        .filter(workOrder -> RUNNING.contains(workOrder.getStatus()))
        .toList();
  }

  private WorkOrderHold load(UUID holdId) {
    return holds
        .findByTenantIdAndId(TenantContext.requireTenantId(), holdId)
        .orElseThrow(() -> new NotFoundException("Work-order hold not found: " + holdId));
  }

  private static UUID actor() {
    UUID actor = TenantContext.getCurrentUserId();
    if (actor == null) {
      throw new IllegalStateException("An authenticated actor is required");
    }
    return actor;
  }

  private static Map<UUID, WorkOrder> byId(List<WorkOrder> values) {
    return values.stream().collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
  }

  private static HoldView view(WorkOrderHold hold, Map<UUID, WorkOrder> workOrders) {
    WorkOrder workOrder = workOrders.get(hold.getWorkOrderId());
    return new HoldView(
        hold.getId(),
        hold.getWorkOrderId(),
        workOrder == null ? null : workOrder.getWorkOrderNumber(),
        hold.getStatus().name(),
        hold.getRequestReason(),
        hold.getRequestedBy(),
        hold.getRequestedAt(),
        hold.getStopNote(),
        hold.getConfirmedAt(),
        hold.getResumeNote(),
        hold.getResumedAt());
  }

  static WorkOrderHoldDtos.HoldDto toDto(WorkOrderHold hold) {
    return new WorkOrderHoldDtos.HoldDto(
        hold.getId(),
        hold.getWorkOrderId(),
        hold.getSalesOrderId(),
        hold.getSalesOrderLineId(),
        hold.getStatus(),
        hold.getRequestReason(),
        hold.getRequestedBy(),
        hold.getRequestedAt(),
        hold.getStopNote(),
        hold.getConfirmedBy(),
        hold.getConfirmedAt(),
        hold.getResumeNote(),
        hold.getResumedBy(),
        hold.getResumedAt());
  }
}
