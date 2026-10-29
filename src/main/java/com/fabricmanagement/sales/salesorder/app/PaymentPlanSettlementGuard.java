package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.finance.collection.app.port.CollectionSettlementPort;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Compares a proposed payment plan with the cash finance has already recorded. Finance reports what
 * was collected; what counts as a reduction is decided here, by sales.
 */
@Component
@RequiredArgsConstructor
public class PaymentPlanSettlementGuard {
  private final CollectionSettlementPort settlement;

  /** One obligation the proposed plan lowers below cash that is already recorded against it. */
  public record Shortfall(
      UUID obligationId,
      String currency,
      BigDecimal settled,
      BigDecimal agreed,
      BigDecimal proposed) {}

  /**
   * Obligations of the agreed plan that the proposal lowers, or drops, to less than has been
   * collected. With no agreed plan nothing can have been collected, so finance is not asked.
   */
  public List<Shortfall> shortfalls(
      UUID tenantId, UUID orderId, PaymentPlanContent agreed, PaymentPlanContent proposed) {
    if (agreed == null) return List.of();
    return shortfalls(agreed, proposed, settlement.settledByObligation(tenantId, orderId));
  }

  /**
   * A line counts only when the proposal actually lowers it below the agreed amount. An obligation
   * that is already over-collected and stays as agreed is not a new reduction, and the report keeps
   * flagging it.
   */
  static List<Shortfall> shortfalls(
      PaymentPlanContent agreed, PaymentPlanContent proposed, Map<UUID, BigDecimal> settled) {
    if (settled.isEmpty()) return List.of();
    Map<UUID, BigDecimal> next = new HashMap<>();
    for (var group : proposed.currencyGroups())
      for (var line : group.lines())
        if (line.obligationId() != null && line.amount() != null)
          next.merge(line.obligationId(), line.amount(), BigDecimal::add);
    List<Shortfall> found = new ArrayList<>();
    for (var group : agreed.currencyGroups())
      for (var line : group.lines()) {
        if (line.obligationId() == null || line.amount() == null) continue;
        BigDecimal cash = settled.get(line.obligationId());
        if (cash == null) continue;
        // A line the proposal leaves out is a line reduced to nothing.
        BigDecimal now = next.getOrDefault(line.obligationId(), BigDecimal.ZERO);
        if (now.compareTo(line.amount()) < 0 && now.compareTo(cash) < 0)
          found.add(new Shortfall(line.obligationId(), group.currency(), cash, line.amount(), now));
      }
    return List.copyOf(found);
  }
}
