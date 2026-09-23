package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adds current order-line display data without changing the stored immutable evidence snapshot. */
final class OrderCoverDisplay {
  private OrderCoverDisplay() {}

  /**
   * The code settlement passes to the draft production request — unchanged from before the planner
   * extraction: the description, else {@code PRODUCT_<id>}, else {@code null} (a free-text line
   * with neither never yields the literal "PRODUCT_null").
   */
  static String productCode(SalesOrderLine line) {
    if (line.getProductDesc() != null && !line.getProductDesc().isBlank()) {
      return line.getProductDesc();
    }
    return line.getProductId() != null ? "PRODUCT_" + line.getProductId() : null;
  }

  /**
   * Required display label. Line creation already requires a product id or a description, so the
   * last fallback — the line id — only covers legacy or imported rows, and a read never fails on
   * them.
   */
  static String label(SalesOrderLine line) {
    String code = productCode(line);
    return code != null ? code : line.getId().toString();
  }

  static OrderCoverEvidenceDto enrich(
      OrderCoverEvidenceDto evidence, List<SalesOrderLine> orderedActiveLines) {
    Map<UUID, Display> displays = new HashMap<>();
    for (int index = 0; index < orderedActiveLines.size(); index++) {
      SalesOrderLine line = orderedActiveLines.get(index);
      displays.put(line.getId(), new Display(index + 1, label(line)));
    }
    return new OrderCoverEvidenceDto(
        evidence.id(),
        evidence.caseId(),
        evidence.revision(),
        evidence.orderVersion(),
        evidence.computedAt(),
        evidence.inputFingerprint(),
        evidence.ruleVersion(),
        evidence.lines().stream().map(line -> enrich(line, displays)).toList());
  }

  private static OrderCoverEvidenceDto.Line enrich(
      OrderCoverEvidenceDto.Line line, Map<UUID, Display> displays) {
    return new OrderCoverEvidenceDto.Line(
        line.lineId(),
        line.lineVersion(),
        line.productId(),
        line.requested(),
        line.suitableFree(),
        line.remainingSuitableFree(),
        line.shortfall(),
        line.suitability(),
        line.competingAllocations().stream()
            .map(
                allocation -> {
                  Display display = displays.get(allocation.lineId());
                  return new OrderCoverEvidenceDto.CompetingAllocation(
                      allocation.lineId(),
                      allocation.quantity(),
                      display == null ? null : display.lineNumber(),
                      display == null ? null : display.label());
                })
            .toList(),
        line.controlReasons(),
        line.sources(),
        line.blockingReasons());
  }

  private record Display(int lineNumber, String label) {}
}
