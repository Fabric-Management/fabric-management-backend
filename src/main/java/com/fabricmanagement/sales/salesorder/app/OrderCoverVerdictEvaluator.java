package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseState;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidence;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.VerdictCode;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import java.util.Set;
import java.util.UUID;

/** One verdict rule shared by order-cover detail and the decision projection. */
public final class OrderCoverVerdictEvaluator {
  private OrderCoverVerdictEvaluator() {}

  public static VerdictCode evaluate(
      OrderCoverCaseState state, Set<UUID> unresolvedLineIds, OrderCoverEvidence evidence) {
    if (state != OrderCoverCaseState.OPEN && state != OrderCoverCaseState.PARTIALLY_SETTLED) {
      return VerdictCode.CASE_CLOSED;
    }
    if (evidence == null) return VerdictCode.NO_EVIDENCE;
    return evidence.getLines().stream()
            .filter(line -> unresolvedLineIds.contains(line.lineId()))
            .anyMatch(OrderCoverVerdictEvaluator::hasCompleteRequirement)
        ? VerdictCode.ACTIONABLE
        : VerdictCode.EVIDENCE_UNKNOWN;
  }

  private static boolean hasCompleteRequirement(OrderCoverEvidenceDto.Line line) {
    return line.blockingReasons().stream()
        .noneMatch(
            reason ->
                reason.equals("REQUIREMENT_COMPLETENESS_UNKNOWN")
                    || reason.contains("UNTYPED_REQUIREMENTS")
                    || reason.contains("UNSPECIFIED:")
                    || reason.contains("UNRESOLVED_SPEC:"));
  }
}
