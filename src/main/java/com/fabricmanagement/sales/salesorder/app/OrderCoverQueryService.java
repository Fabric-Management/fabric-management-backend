package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.web.AppRoutes;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCapabilityPort;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverFollowQueryPort;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.VerdictCode;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReservationPort;
import com.fabricmanagement.sales.salesorder.dto.*;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderCoverQueryService implements OrderCoverFollowQueryPort {
  private final OrderCoverObjectAccess objectAccess;
  private final SalesOrderRepository orders;
  private final OrderCoverCaseRepository cases;
  private final OrderCoverCaseLineRepository caseLines;
  private final OrderCoverEvidenceRepository evidence;
  private final SalesOrderLineRepository orderLines;
  private final OrderCoverResultRepository results;
  private final OrderCoverLineResultRepository lineResults;
  private final OrderCoverCapabilityPort capabilities;
  private final SalesOrderReservationPort reservations;
  private final ProductionOrderPort production;
  private final Clock clock;

  @Transactional(readOnly = true)
  public void assertReadable(UUID orderId, UUID actorId) {
    readableOrder(orderId, actorId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> caseForTask(UUID tenantId, UUID taskId) {
    return cases.findByTenantIdAndTaskId(tenantId, taskId).map(OrderCoverCase::getId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> orderCreator(UUID tenantId, UUID caseId) {
    return cases
        .findByTenantIdAndId(tenantId, caseId)
        .flatMap(value -> orders.findByTenantIdAndId(tenantId, value.getSalesOrderId()))
        .map(SalesOrder::getCreatedBy);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SettlementSource> settlement(UUID tenantId, UUID caseId, UUID resultId) {
    return results
        .findByTenantIdAndCaseIdAndId(tenantId, caseId, resultId)
        .map(
            value -> new SettlementSource(value.getId(), value.getActorId(), value.getActorKind()));
  }

  @Transactional(readOnly = true)
  public OrderCoverDetail detail(UUID orderId, UUID actorId) {
    SalesOrder order = objectAccess.readable(orderId, actorId);
    UUID tenant = TenantContext.requireTenantId();
    OrderCoverCase coverCase =
        cases
            .findByTenantIdAndSalesOrderId(tenant, orderId)
            .orElseThrow(() -> new NotFoundException("Order-cover case not found"));
    List<OrderCoverCaseLine> scopeLines =
        caseLines.findAllByTenantIdAndCaseIdOrderBySalesOrderLineId(tenant, coverCase.getId());
    List<UUID> unresolved =
        scopeLines.stream()
            .filter(OrderCoverCaseLine::unresolved)
            .map(OrderCoverCaseLine::getSalesOrderLineId)
            .toList();
    List<SalesOrderLine> orderedLines =
        orderLines.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
            tenant, orderId);
    var latest =
        evidence.findFirstByTenantIdAndCaseIdOrderByRevisionDesc(tenant, coverCase.getId());
    var resultDtos =
        results.findAllByTenantIdAndCaseIdOrderByRecordedAtAsc(tenant, coverCase.getId()).stream()
            .map(
                r ->
                    OrderCoverResultDto.from(
                        r, lineResults.findAllByResultIdOrderByLineId(r.getId())))
            .toList();
    boolean open =
        coverCase.getState() == OrderCoverCaseState.OPEN
            || coverCase.getState() == OrderCoverCaseState.PARTIALLY_SETTLED;
    boolean hasActionableEvidence =
        OrderCoverVerdictEvaluator.evaluate(
                coverCase.getState(), Set.copyOf(unresolved), latest.orElse(null))
            == VerdictCode.ACTIONABLE;
    var capability =
        capabilities.evaluate(
            tenant, orderId, coverCase.getTaskId(), actorId, open, hasActionableEvidence);
    List<UUID> direct = capability.directAssigneeIds();
    String blockedReason = capability.blockedReason();
    Map<UUID, OrderCoverCaseLine> scopeByLine =
        scopeLines.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    OrderCoverCaseLine::getSalesOrderLineId,
                    java.util.function.Function.identity()));
    Map<UUID, OrderCoverEvidenceDto.Line> evidenceByLine =
        latest
            .map(
                snapshot ->
                    snapshot.getLines().stream()
                        .collect(
                            java.util.stream.Collectors.toMap(
                                OrderCoverEvidenceDto.Line::lineId,
                                java.util.function.Function.identity())))
            .orElseGet(Map::of);
    List<OrderCoverLineDecision> decisions =
        lineDecisions(
            tenant, orderedLines, scopeByLine, evidenceByLine, latest.orElse(null), blockedReason);
    if (decisions.size() != scopeLines.size()) {
      // A scope line whose order line is no longer active has no position on the order screen,
      // so it cannot carry a line number. It is left out of the read instead of failing the whole
      // case; settlement still rejects it as LINE_NOT_OPEN if it is ever submitted.
      log.warn(
          "Order-cover case {} has {} scope line(s) without an active order line; omitted from"
              + " line decisions",
          coverCase.getId(),
          scopeLines.size() - decisions.size());
    }
    return new OrderCoverDetail(
        new OrderCoverCaseDto(
            coverCase.getId(),
            orderId,
            coverCase.getRevision(),
            coverCase.getState(),
            coverCase.getTaskId(),
            capability.taskVersion(),
            unresolved,
            latest.map(OrderCoverEvidence::getId).orElse(null)),
        new OrderCoverDetail.DecisionSubjectRef(
            OrderCoverDetail.DecisionSubjectType.SALES_ORDER,
            orderId,
            order.getOrderNumber(),
            AppRoutes.salesOrder(orderId)),
        new OrderCoverDetail.DecisionAssignment(
            direct.contains(actorId)
                ? OrderCoverDetail.DecisionAssignmentBucket.MINE
                : direct.isEmpty()
                    ? OrderCoverDetail.DecisionAssignmentBucket.UNASSIGNED
                    : OrderCoverDetail.DecisionAssignmentBucket.DEPARTMENT,
            direct,
            List.of(),
            null),
        latest.map(value -> OrderCoverDisplay.enrich(value.toDto(), orderedLines)).orElse(null),
        List.of(
            new OrderCoverDetail.DecisionCapability(
                OrderCoverDetail.DecisionCapabilityAction.CONFIRM_PRODUCTION_COVER,
                blockedReason == null,
                blockedReason == null
                    ? null
                    : new OrderCoverDetail.DecisionBlockedReason(
                        OrderCoverDetail.DecisionBlockedReasonCode.valueOf(blockedReason),
                        "decision.blocked." + blockedReason.toLowerCase(),
                        new OrderCoverDetail.DecisionReasonParameters(
                            null, capability.taskVersion())),
                null,
                false,
                clock.instant(),
                List.of(PermissionKey.FLOWBOARD_WRITE, PermissionKey.SALES_WRITE))),
        decisions,
        resultDtos);
  }

  private List<OrderCoverLineDecision> lineDecisions(
      UUID tenant,
      List<SalesOrderLine> orderedLines,
      Map<UUID, OrderCoverCaseLine> scopeByLine,
      Map<UUID, OrderCoverEvidenceDto.Line> evidenceByLine,
      OrderCoverEvidence latest,
      String caseBlockedReason) {
    List<OrderCoverLineDecision> decisions = new ArrayList<>();
    for (int index = 0; index < orderedLines.size(); index++) {
      SalesOrderLine line = orderedLines.get(index);
      OrderCoverCaseLine scopeLine = scopeByLine.get(line.getId());
      if (scopeLine == null) continue;
      if (latest == null) {
        decisions.add(
            decision(
                line,
                index + 1,
                null,
                null,
                false,
                new OrderCoverLineBlockReason(
                    OrderCoverLineBlockReason.Code.NO_EVIDENCE, List.of(), null),
                null,
                false));
        continue;
      }
      OrderCoverEvidenceDto.Line evidenceLine = evidenceByLine.get(line.getId());
      UUID lineId = line.getId();
      var assessment =
          OrderCoverLinePlanner.assess(
              scopeLine,
              line,
              evidenceLine,
              () -> reservations.hasActiveReservation(lineId),
              () -> production.hasActiveProduction(tenant, lineId));
      OrderCoverLineBlockReason reason = null;
      boolean selectable = assessment.selectable() && caseBlockedReason == null;
      if (!assessment.selectable()) {
        reason =
            new OrderCoverLineBlockReason(
                OrderCoverLineBlockReason.Code.valueOf(assessment.blockCode().name()),
                assessment.incompleteReasons(),
                null);
      } else if (caseBlockedReason != null) {
        reason =
            new OrderCoverLineBlockReason(
                OrderCoverLineBlockReason.Code.ACTION_NOT_ALLOWED,
                List.of(),
                OrderCoverDetail.DecisionBlockedReasonCode.valueOf(caseBlockedReason));
      }
      decisions.add(
          decision(
              line,
              index + 1,
              latest.getId(),
              latest.getRevision(),
              selectable,
              reason,
              selectable
                  ? OrderCoverEvidenceDto.Quantity.known(
                      assessment.productionQuantity(), line.getUnit())
                  : null,
              assessment.rationaleRequiredIfSelected()));
    }
    return List.copyOf(decisions);
  }

  private static OrderCoverLineDecision decision(
      SalesOrderLine line,
      int lineNumber,
      UUID evidenceId,
      Long evidenceRevision,
      boolean selectable,
      OrderCoverLineBlockReason reason,
      OrderCoverEvidenceDto.Quantity productionQuantity,
      boolean rationaleRequired) {
    return new OrderCoverLineDecision(
        line.getId(),
        lineNumber,
        OrderCoverDisplay.label(line),
        line.getUnit(),
        evidenceId,
        evidenceRevision,
        selectable,
        reason,
        productionQuantity,
        rationaleRequired);
  }

  @Transactional(readOnly = true)
  public OrderCoverResultDto result(UUID orderId, UUID resultId, UUID actorId) {
    objectAccess.readable(orderId, actorId);
    UUID tenant = TenantContext.requireTenantId();
    var result =
        results
            .findByTenantIdAndSalesOrderIdAndId(tenant, orderId, resultId)
            .orElseThrow(() -> new NotFoundException("Order-cover result not found"));
    return OrderCoverResultDto.from(result, lineResults.findAllByResultIdOrderByLineId(resultId));
  }

  private SalesOrder readableOrder(UUID orderId, UUID actorId) {
    return objectAccess.readable(orderId, actorId);
  }
}
