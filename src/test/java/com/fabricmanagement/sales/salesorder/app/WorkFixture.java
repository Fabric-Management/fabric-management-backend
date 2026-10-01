package com.fabricmanagement.sales.salesorder.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.common.app.WorkScopeResolver;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignment;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignmentEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderWorkAssignmentEventRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderWorkAssignmentRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A real {@link OrderWorkService} over in-memory assignments, with users whose departments and
 * permission scopes the test declares.
 */
public final class WorkFixture {

  public final List<OrderWorkAssignmentEvent> events = new ArrayList<>();
  private final Map<String, OrderWorkAssignment> stored = new LinkedHashMap<>();
  private final Map<UUID, WorkScopeResolver.WorkActor> actors = new HashMap<>();
  private final Map<UUID, SalesOrder> orders = new HashMap<>();
  private final Set<UUID> inactive = new java.util.HashSet<>();
  public final OrderWorkService service;

  public WorkFixture(Clock clock) {
    OrderWorkAssignmentRepository assignments = mock(OrderWorkAssignmentRepository.class);
    OrderWorkAssignmentEventRepository eventRepository =
        mock(OrderWorkAssignmentEventRepository.class);
    WorkScopeResolver scopes = mock(WorkScopeResolver.class);
    UserQueryService users = mock(UserQueryService.class);
    SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
    when(orderRepository.findByTenantIdAndId(any(), any()))
        .thenAnswer(invocation -> Optional.ofNullable(orders.get(invocation.getArgument(1))));
    when(users.isActive(any(), any()))
        .thenAnswer(invocation -> !inactive.contains(invocation.<UUID>getArgument(1)));
    when(assignments.save(any(OrderWorkAssignment.class)))
        .thenAnswer(
            invocation -> {
              OrderWorkAssignment value = invocation.getArgument(0);
              if (value.getId() == null) {
                ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
              }
              stored.put(key(value.getSalesOrderId(), value.getKind()), value);
              return value;
            });
    when(assignments.lockByOrderAndKind(any(), any(), any()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(
                    stored.get(key(invocation.getArgument(1), invocation.getArgument(2)))));
    when(assignments.findByTenantIdAndSalesOrderIdAndKind(any(), any(), any()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(
                    stored.get(key(invocation.getArgument(1), invocation.getArgument(2)))));
    when(assignments.findByTenantIdAndKindAndSalesOrderIdIn(any(), any(), anyCollection()))
        .thenAnswer(
            invocation -> {
              OrderWorkKind kind = invocation.getArgument(1);
              Collection<UUID> ids = invocation.getArgument(2);
              return stored.values().stream()
                  .filter(value -> value.getKind() == kind)
                  .filter(value -> ids.contains(value.getSalesOrderId()))
                  .toList();
            });
    when(assignments.findByTenantIdAndKindOrderByRoutedAtAsc(any(), any()))
        .thenAnswer(
            invocation ->
                stored.values().stream()
                    .filter(value -> value.getKind() == invocation.getArgument(1))
                    .toList());
    when(eventRepository.save(any(OrderWorkAssignmentEvent.class)))
        .thenAnswer(
            invocation -> {
              events.add(invocation.getArgument(0));
              return invocation.getArgument(0);
            });
    when(eventRepository.findByTenantIdAndSalesOrderIdAndKindOrderByOccurredAtDesc(
            any(), any(), any()))
        .thenAnswer(invocation -> List.copyOf(events).reversed());
    when(scopes.actor(any(), any(), anyBoolean()))
        .thenAnswer(
            invocation -> {
              UUID userId = invocation.getArgument(1);
              return actors.getOrDefault(
                  userId, new WorkScopeResolver.WorkActor(userId, Set.of(), null));
            });
    when(users.findById(any(), any()))
        .thenAnswer(
            invocation -> {
              UserDto user = new UserDto();
              user.setDisplayName("User " + invocation.getArgument(1).toString().substring(0, 4));
              return Optional.of(user);
            });
    when(users.findActiveUserIdsByDepartmentCodes(any(), any()))
        .thenAnswer(
            invocation -> {
              Set<String> codes = invocation.getArgument(1);
              return actors.values().stream()
                  .filter(actor -> !inactive.contains(actor.userId()))
                  .filter(actor -> codes.stream().anyMatch(actor::inDepartment))
                  .map(WorkScopeResolver.WorkActor::userId)
                  .collect(java.util.stream.Collectors.toSet());
            });
    service =
        new OrderWorkService(assignments, eventRepository, scopes, users, orderRepository, clock);
  }

  /** Declares a user in a department with the given permission scopes. */
  public UUID user(String department, Map<PermissionKey, DataScope> grants) {
    return user(UUID.randomUUID(), department, grants);
  }

  public UUID user(UUID id, String department, Map<PermissionKey, DataScope> grants) {
    Map<String, Map<String, DataScope>> permissions = new HashMap<>();
    grants.forEach(
        (key, scope) ->
            permissions
                .computeIfAbsent(key.resource(), ignored -> new HashMap<>())
                .put(key.action(), scope));
    actors.put(
        id,
        new WorkScopeResolver.WorkActor(
            id, Set.of(department), new PermissionResult(permissions, false)));
    return id;
  }

  /** A planning-team member: works on what they took, takes from the team's queue. */
  public UUID planner() {
    return user(
        "PLANNING",
        Map.of(
            PermissionKey.PRODUCTION_READ, DataScope.OWN,
            PermissionKey.PRODUCTION_WRITE, DataScope.OWN,
            PermissionKey.PRODUCTION_CLAIM, DataScope.DEPARTMENT));
  }

  /** A planning supervisor: works on and assigns the team's orders. */
  public UUID planningSupervisor() {
    return user(
        "PLANNING",
        Map.of(
            PermissionKey.PRODUCTION_READ, DataScope.DEPARTMENT,
            PermissionKey.PRODUCTION_WRITE, DataScope.DEPARTMENT,
            PermissionKey.PRODUCTION_CLAIM, DataScope.DEPARTMENT,
            PermissionKey.PRODUCTION_ASSIGN, DataScope.DEPARTMENT));
  }

  /** The orders whose status the work checks (closed orders take no work). */
  public void order(SalesOrder order) {
    orders.put(order.getId(), order);
  }

  public void deactivate(UUID userId) {
    inactive.add(userId);
  }

  public OrderWorkAssignment stored(UUID orderId, OrderWorkKind kind) {
    return stored.get(key(orderId, kind));
  }

  private static String key(Object orderId, Object kind) {
    return orderId + "|" + kind;
  }
}
