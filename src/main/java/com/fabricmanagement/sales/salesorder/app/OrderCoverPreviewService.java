package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverCase;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseLine;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseState;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidence;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCapabilityPort;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.VerdictCode;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReservationPort;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverLineBlockReason;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverSelectionPreview;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverSelectionPreviewRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverCaseLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverCaseRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverEvidenceRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCoverPreviewService {
  private final OrderCoverObjectAccess objectAccess;
  private final OrderCoverCaseRepository cases;
  private final OrderCoverCaseLineRepository caseLines;
  private final SalesOrderLineRepository orderLines;
  private final OrderCoverEvidenceRepository evidence;
  private final OrderCoverEvidenceService evidenceService;
  private final OrderCoverCapabilityPort capabilities;
  private final SalesOrderReservationPort reservations;
  private final ProductionOrderPort production;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public OrderCoverSelectionPreview preview(
      UUID orderId, UUID actorId, OrderCoverSelectionPreviewRequest request) {
    objectAccess.readable(orderId, actorId);
    UUID tenant = TenantContext.requireTenantId();
    OrderCoverCase coverCase =
        cases
            .findByTenantIdAndSalesOrderId(tenant, orderId)
            .orElseThrow(() -> new NotFoundException("Order-cover case not found"));
    OrderCoverEvidence submitted =
        evidence
            .findByTenantIdAndSalesOrderIdAndId(tenant, orderId, request.evidenceId())
            .orElse(null);
    var latest =
        evidence.findFirstByTenantIdAndCaseIdOrderByRevisionDesc(tenant, coverCase.getId());
    if (submitted == null
        || !submitted.getCaseId().equals(coverCase.getId())
        || submitted.getRevision() != request.evidenceRevision()
        || latest.isEmpty()
        || !latest.get().getId().equals(submitted.getId())
        || !evidenceService.revalidateLockless(orderId, submitted.getId()).matches()) {
      throw changedEvidence();
    }

    List<OrderCoverCaseLine> scope =
        caseLines.findAllByTenantIdAndCaseIdOrderBySalesOrderLineId(tenant, coverCase.getId());
    Map<UUID, OrderCoverCaseLine> scopeByLine =
        scope.stream()
            .collect(
                Collectors.toMap(OrderCoverCaseLine::getSalesOrderLineId, Function.identity()));
    List<SalesOrderLine> orderedLines =
        orderLines.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
            tenant, orderId);
    Map<UUID, SalesOrderLine> orderById =
        orderedLines.stream().collect(Collectors.toMap(SalesOrderLine::getId, Function.identity()));
    Map<UUID, Integer> lineNumbers =
        java.util.stream.IntStream.range(0, orderedLines.size())
            .boxed()
            .collect(
                Collectors.toMap(index -> orderedLines.get(index).getId(), index -> index + 1));
    Map<UUID, OrderCoverEvidenceDto.Line> evidenceByLine =
        submitted.getLines().stream()
            .collect(Collectors.toMap(OrderCoverEvidenceDto.Line::lineId, Function.identity()));

    List<OrderCoverLinePlanner.LineAssessment> assessments = new ArrayList<>();
    List<OrderCoverSelectionPreview.Line> previewLines = new ArrayList<>();
    for (UUID lineId : request.lineIds()) {
      SalesOrderLine line = orderById.get(lineId);
      OrderCoverCaseLine scopeLine = scopeByLine.get(lineId);
      OrderCoverEvidenceDto.Line evidenceLine = evidenceByLine.get(lineId);
      var assessment =
          OrderCoverLinePlanner.assess(
              scopeLine,
              line,
              evidenceLine,
              () -> reservations.hasActiveReservation(lineId),
              () -> production.hasActiveProduction(tenant, lineId));
      assessments.add(assessment);
      if (!assessment.selectable()) {
        return OrderCoverSelectionPreview.rejected(
            OrderCoverLineBlockReason.Code.valueOf(assessment.blockCode().name()),
            lineId,
            assessment.incompleteReasons(),
            null);
      }
      previewLines.add(
          new OrderCoverSelectionPreview.Line(
              lineId,
              lineNumbers.get(lineId),
              OrderCoverDisplay.label(line),
              OrderCoverEvidenceDto.Quantity.known(
                  assessment.productionQuantity(), line.getUnit())));
    }

    Set<UUID> unresolved =
        scope.stream()
            .filter(OrderCoverCaseLine::unresolved)
            .map(OrderCoverCaseLine::getSalesOrderLineId)
            .collect(Collectors.toSet());
    boolean open =
        coverCase.getState() == OrderCoverCaseState.OPEN
            || coverCase.getState() == OrderCoverCaseState.PARTIALLY_SETTLED;
    boolean actionable =
        OrderCoverVerdictEvaluator.evaluate(coverCase.getState(), unresolved, submitted)
            == VerdictCode.ACTIONABLE;
    var capability =
        capabilities.evaluate(tenant, orderId, coverCase.getTaskId(), actorId, open, actionable);
    if (!capability.allowed()) {
      return OrderCoverSelectionPreview.rejected(
          OrderCoverLineBlockReason.Code.ACTION_NOT_ALLOWED,
          null,
          List.of(),
          OrderCoverDetail.DecisionBlockedReasonCode.valueOf(capability.blockedReason()));
    }
    return new OrderCoverSelectionPreview(
        true,
        null,
        previewLines,
        OrderCoverLinePlanner.assessSelection(assessments).rationaleRequired());
  }

  private static OrderCoverConflictException changedEvidence() {
    return new OrderCoverConflictException(
        "Submitted evidence is no longer current", "EVIDENCE_CHANGED");
  }
}
