package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.sales.orderintake.dto.OrderIntakeRequests;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the quantity tolerance agreed with the customer (SOI A03). There is no default: without a
 * recorded agreement every whole-piece option is shown with its explicit difference.
 */
@Service
@RequiredArgsConstructor
public class AgreedToleranceService {

  private final OrderIntakeAccess access;
  private final SalesOrderRepository orders;
  private final Clock clock;

  @Transactional(readOnly = true)
  public OrderIntakeViews.AgreedTolerance current(UUID orderId, UUID actor) {
    return OrderIntakeViews.AgreedTolerance.from(access.readableOrder(orderId, actor));
  }

  @Transactional
  public OrderIntakeViews.AgreedTolerance record(
      UUID orderId, OrderIntakeRequests.RecordAgreedTolerance request, UUID actor) {
    SalesOrder order = access.writableOrder(orderId, actor);
    order.recordAgreedTolerance(
        request.upPct(), request.downPct(), request.source(), actor, Instant.now(clock));
    SalesOrder saved = orders.save(order);
    return OrderIntakeViews.AgreedTolerance.from(saved);
  }
}
