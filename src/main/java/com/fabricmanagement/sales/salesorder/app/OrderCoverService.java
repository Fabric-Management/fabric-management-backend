package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseChangedEvent;
import com.fabricmanagement.sales.salesorder.domain.port.*;
import com.fabricmanagement.sales.salesorder.dto.*;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCoverService implements OrderCoverCommandPort {
  private final OrderCoverCaseRepository cases;
  private final OrderCoverCaseLineRepository caseLines;
  private final OrderCoverEvidenceRepository evidenceRepository;
  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final OrderCoverEvidenceService evidenceService;
  private final OrderCoverResultRepository results;
  private final OrderCoverLineResultRepository lineResults;
  private final ProductionOrderPort production;
  private final SalesOrderReservationPort reservations;
  private final com.fabricmanagement.common.infrastructure.events.DomainEventPublisher events;
  private final Clock clock;
  private final com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock
      fulfilmentLock;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Decision confirm(
      UUID tenantId, UUID orderId, UUID actorId, ConfirmProductionCoverPayload payload) {
    if (!tenantId.equals(TenantContext.requireTenantId())) return rejected("TENANT_MISMATCH");
    SalesOrder order = orders.lockByTenantIdAndId(tenantId, orderId).orElse(null);
    if (order == null || order.getCoverRegime() != OrderCoverRegime.GOVERNED)
      return rejected("ORDER_NOT_GOVERNED");
    if (order.getStatus() == OrderStatus.CANCELLED || !Boolean.TRUE.equals(order.getIsActive()))
      return rejected("ORDER_NOT_ACTIVE");
    OrderCoverCase coverCase = cases.lock(tenantId, payload.caseId()).orElse(null);
    if (coverCase == null || !coverCase.getSalesOrderId().equals(orderId))
      return rejected("CASE_MISMATCH");
    if (coverCase.getState() == OrderCoverCaseState.CANCELLED
        || coverCase.getState() == OrderCoverCaseState.SETTLED) return rejected("CASE_CLOSED");
    Map<UUID, OrderCoverCaseLine> scope =
        caseLines.lockAll(tenantId, coverCase.getId()).stream()
            .collect(
                Collectors.toMap(OrderCoverCaseLine::getSalesOrderLineId, Function.identity()));
    Map<UUID, SalesOrderLine> orderLines =
        lines.lockAllForOrder(tenantId, orderId).stream()
            .collect(Collectors.toMap(SalesOrderLine::getId, Function.identity()));
    fulfilmentLock.lockAll(tenantId, payload.lineIds());
    OrderCoverEvidence evidence =
        evidenceRepository
            .findByTenantIdAndSalesOrderIdAndId(tenantId, orderId, payload.evidenceId())
            .orElse(null);
    if (evidence == null
        || !evidence.getCaseId().equals(coverCase.getId())
        || evidence.getRevision() != payload.evidenceRevision())
      throw new OrderCoverConflictException(
          "Submitted evidence is no longer current", "EVIDENCE_CHANGED");
    var latest =
        evidenceRepository.findFirstByTenantIdAndCaseIdOrderByRevisionDesc(
            tenantId, coverCase.getId());
    if (latest.isEmpty()
        || !latest.get().getId().equals(evidence.getId())
        || !evidenceService.revalidate(orderId, evidence.getId()).matches())
      throw new OrderCoverConflictException(
          "Submitted evidence is no longer current", "EVIDENCE_CHANGED");

    Map<UUID, OrderCoverEvidenceDto.Line> evidenceLines =
        evidence.getLines().stream()
            .collect(Collectors.toMap(OrderCoverEvidenceDto.Line::lineId, Function.identity()));
    List<Plan> plans = new ArrayList<>();
    for (UUID lineId : payload.lineIds()) {
      OrderCoverCaseLine scopeLine = scope.get(lineId);
      SalesOrderLine line = orderLines.get(lineId);
      OrderCoverEvidenceDto.Line lineEvidence = evidenceLines.get(lineId);
      var assessment =
          OrderCoverLinePlanner.assess(
              scopeLine,
              line,
              lineEvidence,
              () -> reservations.hasActiveReservation(lineId),
              () -> production.hasActiveProduction(tenantId, lineId));
      if (!assessment.selectable()) return rejected(LegacyRejectionCode.of(assessment));
      plans.add(new Plan(scopeLine, line, lineEvidence, assessment));
    }
    boolean rationaleRequired =
        OrderCoverLinePlanner.assessSelection(plans.stream().map(Plan::assessment).toList())
            .rationaleRequired();
    if (rationaleRequired && (payload.rationale() == null || payload.rationale().isBlank()))
      return rejected("RATIONALE_REQUIRED");

    var result =
        results.saveAndFlush(
            OrderCoverResult.record(
                tenantId,
                coverCase.getId(),
                coverCase.getRevision() + 1,
                orderId,
                actorId,
                normalize(payload.rationale()),
                normalize(payload.note()),
                evidence.getId(),
                evidence.getRevision(),
                clock.instant()));
    for (Plan plan : plans) {
      SalesOrderLine line = plan.line();
      var profile = line.getRequirementProfileSnapshot();
      UUID workOrderId =
          production.requestDraftProductionOrder(
              new DraftProductionOrderCommand(
                  line.getRecipeId(),
                  order.getTradingPartnerId(),
                  line.getId(),
                  plan.assessment().productionQuantity(),
                  line.getUnit(),
                  line.getCurrency(),
                  order.getDeadline(),
                  certification(profile),
                  origin(profile),
                  orderId,
                  line.getProductId(),
                  profile.profileId(),
                  profile.profileVersion(),
                  profile,
                  OrderCoverDisplay.productCode(line)));
      if (line.getRecipeId() == null)
        events.publish(
            new com.fabricmanagement.common.domain.event.production
                .WorkOrderRecipeAssignmentNeededEvent(
                tenantId, workOrderId, line.getId(), certification(profile), origin(profile)));
      lineResults.save(
          OrderCoverLineResult.makeToOrder(
              tenantId,
              result.getId(),
              line.getId(),
              plan.assessment().productionQuantity(),
              line.getUnit(),
              plan.evidence().suitability(),
              profile.profileId(),
              profile.profileVersion(),
              plan.evidence().sources(),
              workOrderId));
      plan.scope().settle(result.getId(), clock.instant());
    }
    caseLines.saveAll(plans.stream().map(Plan::scope).toList());
    Set<UUID> remaining =
        scope.values().stream()
            .filter(OrderCoverCaseLine::unresolved)
            .map(OrderCoverCaseLine::getSalesOrderLineId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    coverCase.settle(remaining.isEmpty(), clock.instant());
    cases.save(coverCase);
    events.publish(
        new OrderCoverCaseChangedEvent(
            tenantId,
            coverCase.getId(),
            coverCase.getRevision(),
            coverCase.getState(),
            result.getId()));
    return new Decision.Accepted(result.getId(), remaining);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void attachTask(UUID tenantId, UUID caseId, UUID taskId) {
    var value = cases.lock(tenantId, caseId).orElseThrow();
    if (value.getState() == OrderCoverCaseState.CANCELLED
        || value.getState() == OrderCoverCaseState.SETTLED)
      throw new IllegalStateException("A closed order-cover case cannot accept a task");
    value.attachTask(taskId);
    cases.save(value);
    events.publish(
        new OrderCoverCaseChangedEvent(
            tenantId, value.getId(), value.getRevision(), value.getState(), null));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public ProvisioningSnapshot currentProvisioning(UUID tenantId, UUID orderId, UUID caseId) {
    var order = orders.lockByTenantIdAndId(tenantId, orderId).orElseThrow();
    var coverCase = cases.lock(tenantId, caseId).orElseThrow();
    if (!coverCase.getSalesOrderId().equals(orderId))
      throw new IllegalStateException("Order-cover event does not match its current case");
    boolean active =
        order.getCoverRegime() == OrderCoverRegime.GOVERNED
            && order.getStatus() != OrderStatus.CANCELLED
            && Boolean.TRUE.equals(order.getIsActive())
            && (coverCase.getState() == OrderCoverCaseState.OPEN
                || coverCase.getState() == OrderCoverCaseState.PARTIALLY_SETTLED);
    Set<UUID> unresolved =
        active
            ? caseLines.lockAll(tenantId, caseId).stream()
                .filter(OrderCoverCaseLine::unresolved)
                .map(OrderCoverCaseLine::getSalesOrderLineId)
                .collect(Collectors.toCollection(LinkedHashSet::new))
            : Set.of();
    return new ProvisioningSnapshot(
        active, order.getId(), order.getOrderNumber(), order.getDeadline(), unresolved);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID cancel(UUID tenantId, UUID orderId) {
    var order = orders.lockByTenantIdAndId(tenantId, orderId).orElseThrow();
    if (order.getStatus() != OrderStatus.CANCELLED)
      throw new IllegalStateException("Order-cover cancellation requires a cancelled sales order");
    var value = cases.lockByOrder(tenantId, orderId).orElseThrow();
    value.cancel(clock.instant());
    cases.save(value);
    events.publish(
        new OrderCoverCaseChangedEvent(
            tenantId, value.getId(), value.getRevision(), value.getState(), null));
    return value.getId();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<UUID> cancelIfPresent(UUID tenantId, UUID orderId) {
    var order = orders.lockByTenantIdAndId(tenantId, orderId).orElseThrow();
    if (order.getStatus() != OrderStatus.CANCELLED)
      throw new IllegalStateException("Order-cover cancellation requires a cancelled sales order");
    var value = cases.lockByOrder(tenantId, orderId);
    value.ifPresent(
        coverCase -> {
          coverCase.cancel(clock.instant());
          cases.save(coverCase);
          events.publish(
              new OrderCoverCaseChangedEvent(
                  tenantId,
                  coverCase.getId(),
                  coverCase.getRevision(),
                  coverCase.getState(),
                  null));
        });
    return value.map(OrderCoverCase::getId);
  }

  private static Decision.Rejected rejected(String code) {
    return new Decision.Rejected(code, "Order-cover precondition failed");
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String certification(
      com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot profile) {
    return profile.facets().stream()
        .filter(
            f ->
                f.kind()
                    == com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet
                        .Kind.CERTIFICATION)
        .map(com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet::value)
        .filter(
            com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue
                    .Certification.class
                ::isInstance)
        .map(
            com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue
                    .Certification.class
                ::cast)
        .flatMap(value -> value.certificates().stream())
        .map(value -> value.scheme())
        .sorted()
        .findFirst()
        .orElse(null);
  }

  private static String origin(
      com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot profile) {
    return profile.facets().stream()
        .filter(
            f ->
                f.kind()
                    == com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet
                        .Kind.ORIGIN)
        .map(com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet::value)
        .filter(
            com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue.Origin
                    .class
                ::isInstance)
        .map(
            com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue.Origin
                    .class
                ::cast)
        .flatMap(value -> value.allowedCountries().stream().sorted())
        .findFirst()
        .orElse(null);
  }

  private record Plan(
      OrderCoverCaseLine scope,
      SalesOrderLine line,
      OrderCoverEvidenceDto.Line evidence,
      OrderCoverLinePlanner.LineAssessment assessment) {}
}
