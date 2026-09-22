package com.fabricmanagement.flowboard.decision.app;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.flowboard.decision.domain.DecisionQueueBucket;
import com.fabricmanagement.flowboard.decision.dto.*;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionQueueRepository;
import com.fabricmanagement.flowboard.routing.app.RoutingEligibilityService;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.domain.port.out.SalesOrderWriteScopePort;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.app.DecisionBlockedException;
import com.fabricmanagement.flowboard.task.domain.OrderCoverActionEvaluator;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.VerdictCode;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReadScopePort;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionQueueService {
  private final DecisionQueueRepository queue;
  private final SalesOrderReadScopePort readScopes;
  private final SalesOrderWriteScopePort writeScopes;
  private final UserFacade users;
  private final RoutingEligibilityService eligibility;
  private final RoutingRepository routing;
  private final DecisionProjectionRebuildState rebuildState;
  private final Clock clock;

  public DecisionQueueService(
      DecisionQueueRepository queue,
      SalesOrderReadScopePort readScopes,
      SalesOrderWriteScopePort writeScopes,
      UserFacade users,
      RoutingEligibilityService eligibility,
      RoutingRepository routing,
      DecisionProjectionRebuildState rebuildState,
      Clock clock) {
    this.queue = queue;
    this.readScopes = readScopes;
    this.writeScopes = writeScopes;
    this.users = users;
    this.eligibility = eligibility;
    this.routing = routing;
    this.rebuildState = rebuildState;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PagedResponse<DecisionQueueItem> list(
      UUID tenant, UUID caller, DecisionQueueBucket bucket, int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Page must be non-negative and size between 1 and 100");
    }
    assertActive(tenant, caller);
    var readScope = readScopes.readScope(tenant, caller);
    var user = eligibility.user(tenant, caller);
    boolean managesRouting =
        user.permissions()
            .can(
                PermissionKey.FLOWBOARD_MANAGE_ROUTING.resource(),
                PermissionKey.FLOWBOARD_MANAGE_ROUTING.action());
    if (bucket == DecisionQueueBucket.UNASSIGNED && !managesRouting) {
      throw new DecisionBlockedException(
          "PERMISSION_DENIED", PermissionKey.FLOWBOARD_MANAGE_ROUTING.key(), null);
    }
    if (readScope.kind() == SalesOrderReadScopePort.Kind.NONE) {
      return PagedResponse.from(new PageImpl<>(List.of(), PageRequest.of(page, size), 0));
    }

    Set<UUID> departments = users.departmentIds(tenant, caller);
    var slice = queue.page(tenant, caller, departments, readScope, bucket, page, size);
    boolean candidate = eligibility.candidacy(tenant, user).isEmpty();
    boolean poolMember =
        candidate && routing.isActiveMember(tenant, RoutingPoolKey.ORDER_COVER, caller);
    Set<UUID> allowedOrders =
        candidate && poolMember
            ? writeScopes.allowedOrderIds(
                tenant,
                caller,
                slice.rows().stream().map(DecisionQueueRepository.Row::orderId).toList())
            : Set.of();
    var items =
        slice.rows().stream()
            .map(row -> item(row, bucket, caller, candidate, poolMember, allowedOrders))
            .toList();
    return PagedResponse.from(new PageImpl<>(items, PageRequest.of(page, size), slice.total()));
  }

  @Transactional(readOnly = true)
  public DecisionQueueSummary summary(UUID tenant, UUID caller) {
    assertActive(tenant, caller);
    var readScope = readScopes.readScope(tenant, caller);
    var user = eligibility.user(tenant, caller);
    boolean managesRouting =
        user.permissions()
            .can(
                PermissionKey.FLOWBOARD_MANAGE_ROUTING.resource(),
                PermissionKey.FLOWBOARD_MANAGE_ROUTING.action());
    if (readScope.kind() == SalesOrderReadScopePort.Kind.NONE) {
      return new DecisionQueueSummary(
          0,
          0,
          managesRouting ? 0L : null,
          0,
          clock.instant(),
          rebuildState.isRunning(tenant)
              || queue.hasOverduePublication(tenant, DecisionQueueFreshness.LISTENERS));
    }
    Set<UUID> departments = users.departmentIds(tenant, caller);
    return new DecisionQueueSummary(
        queue.count(tenant, caller, departments, readScope, DecisionQueueBucket.MINE),
        queue.count(tenant, caller, departments, readScope, DecisionQueueBucket.DEPARTMENT),
        managesRouting
            ? queue.count(tenant, caller, departments, readScope, DecisionQueueBucket.UNASSIGNED)
            : null,
        queue.count(tenant, caller, departments, readScope, DecisionQueueBucket.WAITING),
        clock.instant(),
        rebuildState.isRunning(tenant)
            || queue.hasOverduePublication(tenant, DecisionQueueFreshness.LISTENERS));
  }

  private DecisionQueueItem item(
      DecisionQueueRepository.Row row,
      DecisionQueueBucket bucket,
      UUID caller,
      boolean candidate,
      boolean poolMember,
      Set<UUID> allowedOrders) {
    var result =
        OrderCoverActionEvaluator.evaluate(
            new OrderCoverActionEvaluator.Inputs(
                true,
                true,
                candidate,
                poolMember,
                allowedOrders.contains(row.orderId()),
                !row.directAssigneeIds().isEmpty() || !row.departmentIds().isEmpty(),
                row.directAssigneeIds(),
                caller,
                VerdictCode.ACTIONABLE.name().equals(row.verdictCode())));
    DecisionBlockedReason reason =
        result.allowed()
            ? null
            : new DecisionBlockedReason(
                DecisionBlockedReasonCode.valueOf(result.blockedReason()),
                "decision.blocked." + result.blockedReason().toLowerCase(Locale.ROOT),
                new DecisionReasonParameters(null, row.taskVersion()));
    var action =
        new DecisionCapability(
            DecisionCapabilityAction.CONFIRM_PRODUCTION_COVER,
            result.allowed(),
            reason,
            null,
            false,
            clock.instant(),
            List.of(PermissionKey.FLOWBOARD_WRITE, PermissionKey.SALES_WRITE));
    String orderHref = "/sales/orders/" + row.orderId();
    return new DecisionQueueItem(
        row.caseId(),
        DecisionQueueItem.Kind.ORDER_COVER,
        row.taskId(),
        row.taskVersion(),
        new DecisionSubjectRef(
            DecisionSubjectType.SALES_ORDER, row.orderId(), row.orderNumber(), orderHref),
        new DecisionAssignment(
            DecisionAssignmentBucket.valueOf(bucket.name()),
            row.directAssigneeIds(),
            row.departmentIds(),
            null),
        Priority.valueOf(row.priority()),
        row.dueDate(),
        row.createdAt(),
        row.projectedAt(),
        bucket == DecisionQueueBucket.WAITING
            ? DecisionQueueItem.State.WAITING
            : DecisionQueueItem.State.OPEN,
        DecisionVerdictCode.valueOf(row.verdictCode()),
        List.of(action),
        orderHref + "/cover");
  }

  private void assertActive(UUID tenant, UUID caller) {
    if (!users.isActive(tenant, caller)) {
      throw new AccessDeniedException("Authenticated user is inactive");
    }
  }
}
