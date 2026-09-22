package com.fabricmanagement.sales.salesorder.domain.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Sales-owned read boundary for the disposable FlowBoard decision projection. */
public interface OrderCoverProjectionPort {
  List<Facts> facts(UUID tenantId, Collection<UUID> caseIds);

  List<UUID> caseIdsAfter(UUID tenantId, UUID afterCaseId, int limit);

  enum VerdictCode {
    ACTIONABLE,
    EVIDENCE_UNKNOWN,
    NO_EVIDENCE,
    CASE_CLOSED
  }

  record Facts(
      UUID tenantId,
      UUID caseId,
      UUID orderId,
      String orderNumber,
      UUID orderCreatedBy,
      UUID taskId,
      String caseState,
      long caseRevision,
      int unresolvedLineCount,
      Instant openedAt,
      Instant closedAt,
      Long evidenceRevision,
      VerdictCode verdictCode) {}
}
