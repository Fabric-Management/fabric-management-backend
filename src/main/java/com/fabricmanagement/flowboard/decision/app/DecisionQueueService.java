package com.fabricmanagement.flowboard.decision.app;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.web.AppRoutes;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.flowboard.collection.app.CollectionFollowUpHealthService;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
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
  private final CollectionFollowUpPort collections;
  private final CollectionFollowUpHealthService collectionHealth;

  public DecisionQueueService(
      DecisionQueueRepository queue,
      SalesOrderReadScopePort readScopes,
      SalesOrderWriteScopePort writeScopes,
      UserFacade users,
      RoutingEligibilityService eligibility,
      RoutingRepository routing,
      DecisionProjectionRebuildState rebuildState,
      Clock clock,
      CollectionFollowUpPort collections,
      CollectionFollowUpHealthService collectionHealth) {
    this.queue = queue;
    this.readScopes = readScopes;
    this.writeScopes = writeScopes;
    this.users = users;
    this.eligibility = eligibility;
    this.routing = routing;
    this.rebuildState = rebuildState;
    this.clock = clock;
    this.collections = collections;
    this.collectionHealth = collectionHealth;
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
    if (!user.active()
        || !tenant.equals(user.tenantId())
        || !user.permissions().can("flowboard", "read"))
      throw new AccessDeniedException("Fresh FlowBoard read access is required");
    boolean managesRouting =
        user.permissions()
            .can(
                PermissionKey.FLOWBOARD_MANAGE_ROUTING.resource(),
                PermissionKey.FLOWBOARD_MANAGE_ROUTING.action());
    if (bucket == DecisionQueueBucket.UNASSIGNED && !managesRouting) {
      throw new DecisionBlockedException(
          "PERMISSION_DENIED", PermissionKey.FLOWBOARD_MANAGE_ROUTING.key(), null);
    }
    var financeScope = financeScope(tenant, caller);
    if (readScope.kind() == SalesOrderReadScopePort.Kind.NONE
        && financeScope.kind() == SalesOrderReadScopePort.Kind.NONE) {
      return PagedResponse.from(new PageImpl<>(List.of(), PageRequest.of(page, size), 0));
    }

    Set<UUID> departments = users.departmentIds(tenant, caller);
    var slice =
        queue.page(tenant, caller, departments, readScope, bucket, page, size, financeScope);
    boolean candidate = eligibility.candidacy(tenant, user).isEmpty();
    boolean poolMember =
        candidate && routing.isActiveMember(tenant, RoutingPoolKey.ORDER_COVER, caller);
    Set<UUID> allowedOrders =
        candidate && poolMember
            ? writeScopes.allowedOrderIds(
                tenant,
                caller,
                slice.rows().stream()
                    .filter(row -> "ORDER_COVER".equals(row.kind()))
                    .map(DecisionQueueRepository.Row::orderId)
                    .toList())
            : Set.of();
    boolean collectionAllowed =
        slice.rows().stream().anyMatch(row -> "COLLECTION_FOLLOW_UP".equals(row.kind()))
            && eligibility.candidacy(tenant, user, RoutingPoolKey.COLLECTION_FOLLOW_UP).isEmpty()
            && routing.isActiveMember(tenant, RoutingPoolKey.COLLECTION_FOLLOW_UP, caller);
    var items =
        slice.rows().stream()
            .map(
                row ->
                    item(
                        row,
                        bucket,
                        caller,
                        candidate,
                        poolMember,
                        allowedOrders,
                        collectionAllowed))
            .toList();
    return PagedResponse.from(new PageImpl<>(items, PageRequest.of(page, size), slice.total()));
  }

  @Transactional(readOnly = true)
  public DecisionQueueSummary summary(UUID tenant, UUID caller) {
    assertActive(tenant, caller);
    var readScope = readScopes.readScope(tenant, caller);
    var user = eligibility.user(tenant, caller);
    if (!user.active()
        || !tenant.equals(user.tenantId())
        || !user.permissions().can("flowboard", "read"))
      throw new AccessDeniedException("Fresh FlowBoard read access is required");
    boolean managesRouting =
        user.permissions()
            .can(
                PermissionKey.FLOWBOARD_MANAGE_ROUTING.resource(),
                PermissionKey.FLOWBOARD_MANAGE_ROUTING.action());
    var financeScope = financeScope(tenant, caller);
    if (readScope.kind() == SalesOrderReadScopePort.Kind.NONE
        && financeScope.kind() == SalesOrderReadScopePort.Kind.NONE) {
      return new DecisionQueueSummary(
          0,
          0,
          managesRouting ? 0L : null,
          0,
          clock.instant(),
          rebuildState.isRunning(tenant)
              || (queue.hasOverduePublication(tenant, DecisionQueueFreshness.LISTENERS)
                  || (user.permissions().can("finance", "read")
                      && collectionHealth.stale(tenant))));
    }
    Set<UUID> departments = users.departmentIds(tenant, caller);
    return new DecisionQueueSummary(
        count(tenant, caller, departments, readScope, DecisionQueueBucket.MINE, financeScope),
        count(tenant, caller, departments, readScope, DecisionQueueBucket.DEPARTMENT, financeScope),
        managesRouting
            ? count(
                tenant,
                caller,
                departments,
                readScope,
                DecisionQueueBucket.UNASSIGNED,
                financeScope)
            : null,
        count(tenant, caller, departments, readScope, DecisionQueueBucket.WAITING, financeScope),
        clock.instant(),
        rebuildState.isRunning(tenant)
            || (queue.hasOverduePublication(tenant, DecisionQueueFreshness.LISTENERS)
                || (user.permissions().can("finance", "read") && collectionHealth.stale(tenant))));
  }

  private DecisionQueueItem item(
      DecisionQueueRepository.Row row,
      DecisionQueueBucket bucket,
      UUID caller,
      boolean candidate,
      boolean poolMember,
      Set<UUID> allowedOrders,
      boolean collectionAllowed) {
    if ("COLLECTION_FOLLOW_UP".equals(row.kind()))
      return collectionItem(row, bucket, caller, collectionAllowed);
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
    String orderHref = AppRoutes.salesOrder(row.orderId());
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
        AppRoutes.orderCoverDecision(row.orderId()));
  }

  private SalesOrderReadScopePort.OrderReadScope financeScope(UUID tenant, UUID caller) {
    var user = eligibility.user(tenant, caller);
    if (!user.permissions().can("finance", "read"))
      return SalesOrderReadScopePort.OrderReadScope.none();
    var scope = collections.readScope(caller);
    return scope.all()
        ? SalesOrderReadScopePort.OrderReadScope.all()
        : SalesOrderReadScopePort.OrderReadScope.principals(scope.principals());
  }

  private long count(
      UUID tenant,
      UUID caller,
      Set<UUID> departments,
      SalesOrderReadScopePort.OrderReadScope sales,
      DecisionQueueBucket bucket,
      SalesOrderReadScopePort.OrderReadScope finance) {
    return queue.count(tenant, caller, departments, sales, bucket, finance);
  }

  private DecisionQueueItem collectionItem(
      DecisionQueueRepository.Row row,
      DecisionQueueBucket bucket,
      UUID caller,
      boolean collectionAllowed) {
    boolean allowed =
        row.directAssigneeIds().contains(caller)
            && collectionAllowed
            && "ACTIONABLE".equals(row.verdictCode());
    var reason =
        allowed
            ? null
            : new DecisionBlockedReason(
                row.directAssigneeIds().isEmpty()
                    ? DecisionBlockedReasonCode.UNASSIGNED
                    : DecisionBlockedReasonCode.ASSIGNED_ELSEWHERE,
                "decision.blocked.collection",
                new DecisionReasonParameters(null, row.taskVersion()));
    return new DecisionQueueItem(
        row.caseId(),
        DecisionQueueItem.Kind.COLLECTION_FOLLOW_UP,
        row.taskId(),
        row.taskVersion(),
        new DecisionSubjectRef(
            DecisionSubjectType.COLLECTION_OBLIGATION, row.orderId(), row.orderNumber(), null),
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
        List.of(
            new DecisionCapability(
                DecisionCapabilityAction.RECORD_COLLECTION_CONTACT,
                allowed,
                reason,
                null,
                false,
                clock.instant(),
                List.of(PermissionKey.FLOWBOARD_WRITE, PermissionKey.FINANCE_READ))),
        AppRoutes.collectionFollowUp(row.caseId()));
  }

  private void assertActive(UUID tenant, UUID caller) {
    if (!users.isActive(tenant, caller)) {
      throw new AccessDeniedException("Authenticated user is inactive");
    }
  }
}
