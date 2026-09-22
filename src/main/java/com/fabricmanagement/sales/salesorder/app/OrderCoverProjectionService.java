package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Consistent, batched source reads for the FlowBoard decision projection. */
@Service
@RequiredArgsConstructor
public class OrderCoverProjectionService implements OrderCoverProjectionPort {
  private final OrderCoverCaseRepository cases;
  private final OrderCoverCaseLineRepository caseLines;
  private final OrderCoverEvidenceRepository evidence;
  private final SalesOrderRepository orders;

  @Override
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      propagation = Propagation.REQUIRES_NEW)
  public List<Facts> facts(UUID tenantId, Collection<UUID> caseIds) {
    if (caseIds == null || caseIds.isEmpty()) return List.of();
    if (caseIds.size() > 100)
      throw new IllegalArgumentException("At most 100 case ids are allowed");
    Set<UUID> requested = Set.copyOf(caseIds);
    List<OrderCoverCase> sourceCases = cases.findAllByTenantIdAndIdIn(tenantId, requested);
    Map<UUID, SalesOrder> orderById =
        orders
            .findAllByTenantIdAndIdIn(
                tenantId,
                sourceCases.stream()
                    .map(OrderCoverCase::getSalesOrderId)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(SalesOrder::getId, Function.identity()));
    Map<UUID, List<OrderCoverCaseLine>> linesByCase =
        caseLines.findAllByTenantIdAndCaseIdIn(tenantId, requested).stream()
            .collect(Collectors.groupingBy(OrderCoverCaseLine::getCaseId));
    Map<UUID, OrderCoverEvidence> latestByCase = new HashMap<>();
    evidence.findAllByTenantIdAndCaseIdInOrderByCaseIdAscRevisionDesc(tenantId, requested).stream()
        .forEach(item -> latestByCase.putIfAbsent(item.getCaseId(), item));

    return sourceCases.stream()
        .sorted(Comparator.comparing(value -> value.getId().toString()))
        .map(
            coverCase -> {
              SalesOrder order = Objects.requireNonNull(orderById.get(coverCase.getSalesOrderId()));
              Set<UUID> unresolved =
                  linesByCase.getOrDefault(coverCase.getId(), List.of()).stream()
                      .filter(OrderCoverCaseLine::unresolved)
                      .map(OrderCoverCaseLine::getSalesOrderLineId)
                      .collect(Collectors.toUnmodifiableSet());
              OrderCoverEvidence latest = latestByCase.get(coverCase.getId());
              return new Facts(
                  tenantId,
                  coverCase.getId(),
                  order.getId(),
                  order.getOrderNumber(),
                  order.getCreatedBy(),
                  coverCase.getTaskId(),
                  coverCase.getState().name(),
                  coverCase.getRevision(),
                  unresolved.size(),
                  coverCase.getCreatedAt(),
                  coverCase.getClosedAt(),
                  latest == null ? null : latest.getRevision(),
                  OrderCoverVerdictEvaluator.evaluate(coverCase.getState(), unresolved, latest));
            })
        .toList();
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      propagation = Propagation.REQUIRES_NEW)
  public List<UUID> caseIdsAfter(UUID tenantId, UUID afterCaseId, int limit) {
    if (limit < 1 || limit > 100)
      throw new IllegalArgumentException("Limit must be between 1 and 100");
    return cases.findIdsAfter(tenantId, afterCaseId, limit);
  }
}
