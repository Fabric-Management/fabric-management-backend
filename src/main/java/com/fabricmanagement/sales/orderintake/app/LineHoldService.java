package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.workorder.api.WorkOrderHoldPort;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The salesperson's hold request on a line's running work (SOI D8, A12). Requesting is not
 * stopping: production confirms the stop and later resumes. A sales agreement never starts or
 * resumes production by itself.
 */
@Service
@RequiredArgsConstructor
public class LineHoldService {

  private final OrderIntakeAccess access;
  private final WorkOrderHoldPort holds;

  @Transactional
  public List<FulfilmentDtos.HoldView> request(
      UUID orderId, UUID lineId, FulfilmentDtos.RequestHold input, UUID actor) {
    SalesOrder order = access.writableOrder(orderId, actor);
    SalesOrderLine line = access.line(order, lineId);
    return holds
        .request(
            TenantContext.requireTenantId(), order.getId(), line.getId(), input.reason(), actor)
        .stream()
        .map(LineHoldService::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FulfilmentDtos.HoldView> forLine(UUID orderId, UUID lineId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    SalesOrderLine line = access.line(order, lineId);
    return holds.forLine(TenantContext.requireTenantId(), line.getId()).stream()
        .map(LineHoldService::toDto)
        .toList();
  }

  private static FulfilmentDtos.HoldView toDto(WorkOrderHoldPort.HoldView value) {
    return new FulfilmentDtos.HoldView(
        value.id(),
        value.workOrderId(),
        value.workOrderNumber(),
        value.status(),
        value.requestReason(),
        value.requestedBy(),
        value.requestedAt(),
        value.stopNote(),
        value.confirmedAt(),
        value.resumeNote(),
        value.resumedAt());
  }
}
