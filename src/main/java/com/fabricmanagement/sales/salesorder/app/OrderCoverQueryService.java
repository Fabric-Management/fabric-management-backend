package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCapabilityPort;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverFollowQueryPort;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.VerdictCode;
import com.fabricmanagement.sales.salesorder.dto.*;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCoverQueryService implements OrderCoverFollowQueryPort {
  private final OrderCoverObjectAccess objectAccess;
  private final SalesOrderRepository orders;
  private final OrderCoverCaseRepository cases;
  private final OrderCoverCaseLineRepository caseLines;
  private final OrderCoverEvidenceRepository evidence;
  private final OrderCoverResultRepository results;
  private final OrderCoverLineResultRepository lineResults;
  private final OrderCoverCapabilityPort capabilities;
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
    List<UUID> unresolved =
        caseLines
            .findAllByTenantIdAndCaseIdOrderBySalesOrderLineId(tenant, coverCase.getId())
            .stream()
            .filter(OrderCoverCaseLine::unresolved)
            .map(OrderCoverCaseLine::getSalesOrderLineId)
            .toList();
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
            "/sales/orders/" + orderId),
        new OrderCoverDetail.DecisionAssignment(
            direct.contains(actorId)
                ? OrderCoverDetail.DecisionAssignmentBucket.MINE
                : direct.isEmpty()
                    ? OrderCoverDetail.DecisionAssignmentBucket.UNASSIGNED
                    : OrderCoverDetail.DecisionAssignmentBucket.DEPARTMENT,
            direct,
            List.of(),
            null),
        latest.map(OrderCoverEvidence::toDto).orElse(null),
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
        resultDtos);
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
