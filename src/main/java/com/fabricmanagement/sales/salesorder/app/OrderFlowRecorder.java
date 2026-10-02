package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFlowEventRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Moves an order between flow stages and keeps the move: who, when and why. */
@Component
@RequiredArgsConstructor
public class OrderFlowRecorder {

  private final OrderFlowEventRepository events;
  private final Clock clock;

  /** Moves {@code order} (locked by the caller) to {@code next}; a move the flow forbids is 409. */
  public void move(SalesOrder order, OrderFlowStage next, String reason, UUID actor) {
    String text = reason == null || reason.isBlank() ? null : reason.trim();
    if (text != null && text.length() > OrderFlowEvent.MAX_REASON_LENGTH) {
      throw new OrderDomainException("The reason is too long");
    }
    OrderFlowStage from = order.moveFlowTo(next);
    events.save(OrderFlowEvent.of(order.getId(), from, next, text, actor, clock.instant()));
  }

  /** A system-written reason clipped to what the record keeps. */
  public static String clip(String reason) {
    if (reason == null) {
      return null;
    }
    return reason.length() <= OrderFlowEvent.MAX_REASON_LENGTH
        ? reason
        : reason.substring(0, OrderFlowEvent.MAX_REASON_LENGTH - 1) + "…";
  }
}
