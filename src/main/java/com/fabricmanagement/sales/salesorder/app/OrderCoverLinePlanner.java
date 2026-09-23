package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Pure line-level order-cover decision shared by reads, previews and settlement. */
public final class OrderCoverLinePlanner {
  private OrderCoverLinePlanner() {}

  /**
   * The single entry point for callers that consult live ports. The reservation and production
   * lookups run lazily and in the fixed order settlement has always used: only after every
   * structural check has passed, reservation before production. Detail, preview and settlement all
   * call this method, so the rule order and the port-call order cannot drift apart.
   */
  public static LineAssessment assess(
      OrderCoverCaseLine scopeLine,
      SalesOrderLine orderLine,
      OrderCoverEvidenceDto.Line evidenceLine,
      BooleanSupplier activeReservation,
      BooleanSupplier activeProduction) {
    LineAssessment structural = assess(scopeLine, orderLine, evidenceLine, false, false);
    if (!structural.selectable()) return structural;
    if (activeReservation.getAsBoolean()) {
      return blocked(BlockCode.ACTIVE_RESERVATION_EXISTS, List.of());
    }
    if (activeProduction.getAsBoolean()) {
      return blocked(BlockCode.ACTIVE_PRODUCTION_EXISTS, List.of());
    }
    return structural;
  }

  /** Pure form over already-known facts; used by the lazy entry point above and by unit tests. */
  public static LineAssessment assess(
      OrderCoverCaseLine scopeLine,
      SalesOrderLine orderLine,
      OrderCoverEvidenceDto.Line evidenceLine,
      boolean activeReservation,
      boolean activeProduction) {
    if (scopeLine == null || orderLine == null || evidenceLine == null || !scopeLine.unresolved()) {
      return blocked(BlockCode.LINE_NOT_OPEN, List.of());
    }
    var profile = orderLine.getRequirementProfileSnapshot();
    if (profile == null || !profile.complete()) {
      List<String> reasons = profile == null ? List.of() : profile.incompleteReasons();
      return reasons.isEmpty()
          ? blocked(BlockCode.REQUIREMENT_COMPLETENESS_UNKNOWN, List.of())
          : blocked(BlockCode.REQUIREMENT_INCOMPLETE, reasons);
    }
    if (orderLine.getLineStatus() != SalesOrderLineStatus.PENDING
        && orderLine.getLineStatus() != SalesOrderLineStatus.RECIPE_ASSIGNED) {
      return blocked(BlockCode.LINE_ALREADY_FULFILLED, List.of());
    }
    BigDecimal unresolved =
        orderLine
            .getRequestedQty()
            .subtract(
                orderLine.getShippedQty() == null ? BigDecimal.ZERO : orderLine.getShippedQty());
    if (unresolved.signum() <= 0) {
      return blocked(BlockCode.LINE_ALREADY_FULFILLED, List.of());
    }
    if (activeReservation) {
      return blocked(BlockCode.ACTIVE_RESERVATION_EXISTS, List.of());
    }
    if (activeProduction) {
      return blocked(BlockCode.ACTIVE_PRODUCTION_EXISTS, List.of());
    }
    var shortfall = evidenceLine.shortfall();
    boolean rationaleRequired =
        shortfall.state() != OrderCoverEvidenceDto.Knowledge.KNOWN
            || new BigDecimal(shortfall.value()).signum() <= 0;
    return new LineAssessment(true, null, List.of(), unresolved, rationaleRequired);
  }

  public static SelectionAssessment assessSelection(List<LineAssessment> assessments) {
    List<LineAssessment> values = List.copyOf(assessments);
    return new SelectionAssessment(
        values.stream().allMatch(LineAssessment::selectable),
        values.stream().filter(value -> !value.selectable()).findFirst().orElse(null),
        values.stream().anyMatch(LineAssessment::rationaleRequiredIfSelected));
  }

  private static LineAssessment blocked(BlockCode code, List<String> incompleteReasons) {
    return new LineAssessment(false, code, incompleteReasons, null, false);
  }

  public enum BlockCode {
    LINE_NOT_OPEN,
    REQUIREMENT_COMPLETENESS_UNKNOWN,
    REQUIREMENT_INCOMPLETE,
    LINE_ALREADY_FULFILLED,
    ACTIVE_RESERVATION_EXISTS,
    ACTIVE_PRODUCTION_EXISTS
  }

  public record LineAssessment(
      boolean selectable,
      BlockCode blockCode,
      List<String> incompleteReasons,
      BigDecimal productionQuantity,
      boolean rationaleRequiredIfSelected) {
    public LineAssessment {
      incompleteReasons = List.copyOf(incompleteReasons == null ? List.of() : incompleteReasons);
    }
  }

  public record SelectionAssessment(
      boolean selectable, LineAssessment rejection, boolean rationaleRequired) {}
}
