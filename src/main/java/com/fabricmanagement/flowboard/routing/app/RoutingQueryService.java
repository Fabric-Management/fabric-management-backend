package com.fabricmanagement.flowboard.routing.app;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.RoutingRecords.*;
import com.fabricmanagement.flowboard.routing.domain.exception.RoutingException;
import com.fabricmanagement.flowboard.routing.domain.port.out.EffectivePermissionUserQueryPort;
import com.fabricmanagement.flowboard.routing.dto.*;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoutingQueryService {
  private final RoutingRepository repository;
  private final RoutingEligibilityService eligibility;
  private final EffectivePermissionUserQueryPort users;
  private final TaskRepository tasks;

  public RoutingPoolResponse pool(UUID tenant, RoutingPoolKey key) {
    var pool = repository.pool(tenant, key).orElse(null);
    return pool == null
        ? new RoutingPoolResponse(key, false, null, List.of())
        : new RoutingPoolResponse(
            key,
            true,
            pool.revision(),
            repository.members(tenant, pool.id()).stream()
                .map(m -> member(tenant, m.userId(), m.active()))
                .toList());
  }

  private RoutingMemberResponse member(UUID tenant, UUID id, boolean active) {
    var user = eligibility.user(tenant, id);
    var reasons = eligibility.candidacy(tenant, user);
    return new RoutingMemberResponse(id, user.displayName(), active, reasons.isEmpty(), reasons);
  }

  public Page<RoutingMemberResponse> candidates(
      UUID tenant, RoutingPoolKey key, String search, Pageable page) {
    Set<UUID> flowboard = users.findUsersWithAction(tenant, PermissionKey.FLOWBOARD_WRITE);
    Set<UUID> sales = users.findUsersWithAction(tenant, PermissionKey.SALES_WRITE);
    String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    var candidates =
        flowboard.stream()
            .filter(sales::contains)
            .sorted()
            .map(id -> member(tenant, id, true))
            .filter(RoutingMemberResponse::candidate)
            .filter(
                m ->
                    term.isEmpty()
                        || (m.displayName() != null
                            && m.displayName().toLowerCase(Locale.ROOT).contains(term)))
            .toList();
    return page(candidates, page);
  }

  public RoutingEligibilityResponse eligibility(UUID tenant, UUID taskId) {
    var task =
        tasks
            .findById(taskId)
            .filter(
                t ->
                    tenant.equals(t.getTenantId())
                        && t.getTaskType().name().equals(RoutingPoolKey.ORDER_COVER.name())
                        && t.getWorkflowDefinitionId() != null
                        && "SALES_ORDER".equals(t.getEntityType()))
            .orElseThrow(
                () ->
                    new RoutingException("Governed task not found", "ROUTING_TASK_NOT_FOUND", 404));
    var pool = repository.pool(tenant, RoutingPoolKey.ORDER_COVER).orElse(null);
    var members = pool == null ? List.<Member>of() : repository.members(tenant, pool.id());
    return new RoutingEligibilityResponse(
        taskId,
        members.stream()
            .map(
                m -> {
                  var reasons =
                      eligibility.eligibility(
                          tenant,
                          task.getEntityId(),
                          eligibility.user(tenant, m.userId()),
                          m.active());
                  return new RoutingMemberEligibilityResponse(
                      m.userId(), reasons.isEmpty(), reasons);
                })
            .toList());
  }

  public Page<RoutingFailureResponse> failures(
      UUID tenant, RoutingPoolKey key, Boolean open, Pageable page) {
    var failures = repository.failures(tenant, key, open, page);
    return failures.map(
        f ->
            new RoutingFailureResponse(
                f.id(),
                f.taskId(),
                f.poolKey(),
                f.reason(),
                f.userId(),
                f.poolRevision(),
                f.occurredAt(),
                f.resolvedAt(),
                repository.alerts(tenant, f.id()).stream()
                    .map(
                        a ->
                            new RoutingAlertResponse(
                                a.recipientId(),
                                a.channel(),
                                a.status(),
                                a.attempts(),
                                a.lastError()))
                    .toList()));
  }

  private static <T> Page<T> page(List<T> rows, Pageable page) {
    int start = (int) Math.min(page.getOffset(), rows.size());
    int end = (int) Math.min((long) start + page.getPageSize(), rows.size());
    return new PageImpl<>(rows.subList(start, end), page, rows.size());
  }
}
