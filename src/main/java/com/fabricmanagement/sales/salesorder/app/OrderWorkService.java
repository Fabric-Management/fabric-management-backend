package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.common.app.WorkScopeResolver;
import com.fabricmanagement.sales.common.app.WorkScopeResolver.WorkActor;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignment;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignmentEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkEventType;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.OrderWorkDtos;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderWorkAssignmentEventRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderWorkAssignmentRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Responsibility for planning, warehouse and shipping work on orders. Work is routed to a team; a
 * member takes it explicitly ("claim") or an authorised person assigns it with a reason. The three
 * conditions of every work action are checked separately and all must hold: the permission for the
 * work, scope over this order's work, and a flow stage that allows the step (checked by the
 * caller). Responsibility never replaces the permission, and a write never grants access by itself.
 *
 * <p>Scope over work: Organization covers all of it; Department covers work routed to one of the
 * user's departments; Own covers work the user is responsible for. Unassigned work is seen and
 * taken through the claim permission (or handled through the assign permission), never through the
 * work permission alone.
 */
@Service
@RequiredArgsConstructor
public class OrderWorkService {

  /** Reason codes the frontend shows for an action that is not available. */
  public static final String NOT_ROUTED = "WORK_NOT_ROUTED";

  public static final String NOT_CLAIMED = "WORK_NOT_CLAIMED";
  public static final String ALREADY_ASSIGNED = "WORK_ALREADY_TAKEN";
  public static final String NO_PERMISSION = "PERMISSION_DENIED";
  public static final String OUT_OF_SCOPE = "WORK_OUT_OF_SCOPE";

  private final OrderWorkAssignmentRepository assignments;
  private final OrderWorkAssignmentEventRepository events;
  private final WorkScopeResolver scopes;
  private final UserQueryService users;
  private final SalesOrderRepository orders;
  private final Clock clock;

  /** The permissions that govern each kind of work. */
  public record WorkPermissions(
      PermissionKey read, PermissionKey work, PermissionKey claim, PermissionKey assign) {}

  public static WorkPermissions permissionsOf(OrderWorkKind kind) {
    return switch (kind) {
      case PLANNING ->
          new WorkPermissions(
              PermissionKey.PRODUCTION_READ,
              PermissionKey.PRODUCTION_WRITE,
              PermissionKey.PRODUCTION_CLAIM,
              PermissionKey.PRODUCTION_ASSIGN);
      case SHIP_READINESS ->
          new WorkPermissions(
              PermissionKey.LOGISTICS_READ,
              PermissionKey.LOGISTICS_PREPARE,
              PermissionKey.LOGISTICS_CLAIM,
              PermissionKey.LOGISTICS_ASSIGN);
      case ARRIVAL_ESTIMATE ->
          new WorkPermissions(
              PermissionKey.LOGISTICS_READ,
              PermissionKey.LOGISTICS_WRITE,
              PermissionKey.LOGISTICS_CLAIM,
              PermissionKey.LOGISTICS_ASSIGN);
    };
  }

  /** What the user may do with one piece of work; a null reason means allowed. */
  public record WorkAccess(
      OrderWorkAssignment assignment,
      boolean visible,
      String workReason,
      String claimReason,
      String assignReason,
      String releaseReason) {

    public boolean canWork() {
      return workReason == null;
    }

    public boolean canClaim() {
      return claimReason == null;
    }
  }

  public WorkActor actor(UUID userId, boolean fresh) {
    return scopes.actor(TenantContext.requireTenantId(), userId, fresh);
  }

  /** Access of the user to the work; the assignment may be null when the work is not routed. */
  public WorkAccess access(OrderWorkAssignment work, OrderWorkKind kind, WorkActor actor) {
    WorkPermissions keys = permissionsOf(kind);
    if (work == null) {
      return new WorkAccess(null, false, NOT_ROUTED, NOT_ROUTED, NOT_ROUTED, NOT_ROUTED);
    }
    DataScope workScope = actor.scope(keys.work());
    DataScope claimScope = actor.scope(keys.claim());
    DataScope assignScope = actor.scope(keys.assign());
    boolean coversQueueForAssign = coversQueue(assignScope, work, actor);
    boolean visible =
        work.isAssigned()
            ? covers(actor.scope(keys.read()), work, actor)
                || covers(workScope, work, actor)
                || coversQueueForAssign
            : coversQueue(claimScope, work, actor) || coversQueueForAssign;

    String workReason;
    if (workScope == null) {
      workReason = NO_PERMISSION;
    } else if (!work.isAssigned()) {
      workReason = NOT_CLAIMED;
    } else {
      workReason = covers(workScope, work, actor) ? null : OUT_OF_SCOPE;
    }

    String claimReason;
    if (work.isAssigned()) {
      claimReason = ALREADY_ASSIGNED;
    } else if (claimScope == null || workScope == null) {
      // Taking work one could not then do is not offered.
      claimReason = NO_PERMISSION;
    } else {
      claimReason = coversQueue(claimScope, work, actor) ? null : OUT_OF_SCOPE;
    }

    String assignReason =
        assignScope == null ? NO_PERMISSION : coversQueueForAssign ? null : OUT_OF_SCOPE;

    String releaseReason;
    if (!work.isAssigned()) {
      releaseReason = NOT_CLAIMED;
    } else {
      releaseReason =
          work.isAssignedTo(actor.userId()) || assignReason == null ? null : OUT_OF_SCOPE;
    }
    return new WorkAccess(work, visible, workReason, claimReason, assignReason, releaseReason);
  }

  /** Scope over work that has a responsible person (or is the user's own). */
  static boolean covers(DataScope scope, OrderWorkAssignment work, WorkActor actor) {
    if (scope == null) {
      return false;
    }
    return switch (scope) {
      case GLOBAL, ORGANIZATION -> true;
      case DEPARTMENT -> actor.inDepartment(work.getDepartmentCode());
      case OWN -> work.isAssignedTo(actor.userId());
    };
  }

  /** Scope over the team's queue: Own never covers work that is not one's own. */
  static boolean coversQueue(DataScope scope, OrderWorkAssignment work, WorkActor actor) {
    if (scope == null) {
      return false;
    }
    return switch (scope) {
      case GLOBAL, ORGANIZATION -> true;
      case DEPARTMENT -> actor.inDepartment(work.getDepartmentCode());
      case OWN -> false;
    };
  }

  @Transactional(readOnly = true)
  public Map<UUID, OrderWorkAssignment> assignments(OrderWorkKind kind, Collection<UUID> orderIds) {
    if (orderIds.isEmpty()) {
      return Map.of();
    }
    return assignments
        .findByTenantIdAndKindAndSalesOrderIdIn(TenantContext.requireTenantId(), kind, orderIds)
        .stream()
        .collect(Collectors.toMap(OrderWorkAssignment::getSalesOrderId, Function.identity()));
  }

  @Transactional(readOnly = true)
  public List<OrderWorkAssignment> allOf(OrderWorkKind kind) {
    return assignments.findByTenantIdAndKindOrderByRoutedAtAsc(
        TenantContext.requireTenantId(), kind);
  }

  /** The work's responsibility, its history and the user's responsibility actions. */
  @Transactional(readOnly = true)
  public OrderWorkDtos.WorkView workView(UUID orderId, OrderWorkKind kind, UUID actorId) {
    OrderWorkAssignment work = find(orderId, kind);
    WorkAccess access = access(work, kind, actor(actorId, false));
    if (work == null || !access.visible()) {
      throw new NotFoundException("Sales order not found: " + orderId);
    }
    return new OrderWorkDtos.WorkView(
        orderId,
        viewContext(actorId).of(work),
        responsibilityActions(access),
        history(orderId, kind));
  }

  @Transactional(readOnly = true)
  public OrderWorkAssignment find(UUID orderId, OrderWorkKind kind) {
    return assignments
        .findByTenantIdAndSalesOrderIdAndKind(TenantContext.requireTenantId(), orderId, kind)
        .orElse(null);
  }

  /**
   * The work the user may do now: it is routed, someone is responsible, the user holds the
   * permission and their scope covers it. Work the user cannot see is reported as missing.
   */
  @Transactional
  public OrderWorkAssignment requireWork(UUID orderId, OrderWorkKind kind, UUID actorId) {
    OrderWorkAssignment work = find(orderId, kind);
    WorkAccess access = access(work, kind, actor(actorId, true));
    if (work == null) {
      throw OrderDomainException.stage(NOT_ROUTED, "This work has not been routed to a team yet");
    }
    if (!access.visible()) {
      throw new NotFoundException("Sales order not found: " + orderId);
    }
    if (NOT_CLAIMED.equals(access.workReason())) {
      throw OrderDomainException.workNotClaimed(
          "Nobody is responsible for this work yet; take it or have it assigned first");
    }
    if (!access.canWork()) {
      throw new AccessDeniedException("Your scope does not cover this order's work");
    }
    return work;
  }

  // ── Routing as part of flow steps ──────────────────────────────────────

  /** Puts the work in the team's queue; work someone already holds stays with them. */
  @Transactional
  public OrderWorkAssignment route(UUID orderId, OrderWorkKind kind, UUID actorId) {
    UUID tenantId = TenantContext.requireTenantId();
    Instant now = clock.instant();
    OrderWorkAssignment work = assignments.lockByOrderAndKind(tenantId, orderId, kind).orElse(null);
    if (work == null) {
      work =
          assignments.save(OrderWorkAssignment.route(orderId, kind, kind.defaultDepartment(), now));
    } else if (work.isAssigned()) {
      return work;
    } else {
      work.reroute(kind.defaultDepartment(), now);
      work = assignments.save(work);
    }
    events.save(
        OrderWorkAssignmentEvent.of(work, OrderWorkEventType.ROUTED, null, null, actorId, now));
    return work;
  }

  /** The flow took the work away (withdrawn, returned): its person is released with the reason. */
  @Transactional
  public void releaseForFlow(UUID orderId, OrderWorkKind kind, String reason, UUID actorId) {
    assignments
        .lockByOrderAndKind(TenantContext.requireTenantId(), orderId, kind)
        .filter(OrderWorkAssignment::isAssigned)
        .ifPresent(
            work -> {
              UUID previous = work.getAssigneeId();
              work.release();
              assignments.save(work);
              events.save(
                  OrderWorkAssignmentEvent.of(
                      work,
                      OrderWorkEventType.RELEASED,
                      previous,
                      reason == null || reason.isBlank() ? "Taken back by the order flow" : reason,
                      actorId,
                      clock.instant()));
            });
  }

  // ── Claim, assign, release ─────────────────────────────────────────────

  /** The user takes unassigned work. Of two simultaneous claims only one succeeds. */
  @Transactional
  public OrderWorkAssignment claim(UUID orderId, OrderWorkKind kind, UUID actorId) {
    return claim(orderId, kind, actorId, () -> {});
  }

  /**
   * Takes unassigned work after checking, in this order: scope (others see nothing), whether
   * someone else already took it, the permissions to take and do it, whether the order is still
   * open, and the caller's flow-stage rule. Nothing changes unless all hold.
   */
  @Transactional
  public OrderWorkAssignment claim(
      UUID orderId, OrderWorkKind kind, UUID actorId, Runnable stageCheck) {
    OrderWorkAssignment work = locked(orderId, kind);
    WorkActor actor = actor(actorId, true);
    WorkAccess access = access(work, kind, actor);
    // A team member who could have taken it learns that someone else did; others see nothing.
    boolean teamQueue = coversQueue(actor.scope(permissionsOf(kind).claim()), work, actor);
    if (!access.visible() && !teamQueue) {
      throw new NotFoundException("Sales order not found: " + orderId);
    }
    if (ALREADY_ASSIGNED.equals(access.claimReason())) {
      work.claim(actorId, clock.instant()); // throws the specific conflict
    }
    if (!access.canClaim()) {
      throw new AccessDeniedException("You may not take this work");
    }
    requireOpen(orderId, kind);
    stageCheck.run();
    work.claim(actorId, clock.instant());
    OrderWorkAssignment saved = assignments.save(work);
    events.save(
        OrderWorkAssignmentEvent.of(
            saved, OrderWorkEventType.CLAIMED, null, null, actorId, clock.instant()));
    return saved;
  }

  /**
   * An authorised person assigns or reassigns the work with a reason. The new person must be able
   * to do the work: hold its permission and belong to the routed team (or have organization scope).
   */
  @Transactional
  public OrderWorkAssignment assign(
      UUID orderId, OrderWorkKind kind, UUID assigneeId, String reason, UUID actorId) {
    requireReason(reason);
    OrderWorkAssignment work = locked(orderId, kind);
    WorkAccess access = access(work, kind, actor(actorId, true));
    if (!access.visible()) {
      throw new NotFoundException("Sales order not found: " + orderId);
    }
    if (access.assignReason() != null) {
      throw new AccessDeniedException("You may not assign this work");
    }
    if (!eligible(work, kind, actor(assigneeId, true))) {
      throw new OrderDomainException(
          "The person cannot do this work: they must be active, hold its permission and be in"
              + " the team");
    }
    requireOpen(orderId, kind);
    UUID previous = work.getAssigneeId();
    work.assign(assigneeId, clock.instant());
    OrderWorkAssignment saved = assignments.save(work);
    events.save(
        OrderWorkAssignmentEvent.of(
            saved, OrderWorkEventType.ASSIGNED, previous, reason, actorId, clock.instant()));
    return saved;
  }

  /** The responsible person or an authorised person gives the work back to the team's queue. */
  @Transactional
  public OrderWorkAssignment release(
      UUID orderId, OrderWorkKind kind, String reason, UUID actorId) {
    requireReason(reason);
    OrderWorkAssignment work = locked(orderId, kind);
    WorkAccess access = access(work, kind, actor(actorId, true));
    if (!access.visible()) {
      throw new NotFoundException("Sales order not found: " + orderId);
    }
    if (NOT_CLAIMED.equals(access.releaseReason())) {
      throw new OrderDomainException("The work is not assigned to anyone", 409);
    }
    if (access.releaseReason() != null) {
      throw new AccessDeniedException("You may not release this work");
    }
    UUID previous = work.getAssigneeId();
    work.release();
    OrderWorkAssignment saved = assignments.save(work);
    events.save(
        OrderWorkAssignmentEvent.of(
            saved, OrderWorkEventType.RELEASED, previous, reason, actorId, clock.instant()));
    return saved;
  }

  /** People the user may assign the work to: team members who hold the work permission. */
  @Transactional(readOnly = true)
  public List<OrderWorkDtos.Candidate> candidates(UUID orderId, OrderWorkKind kind, UUID actorId) {
    OrderWorkAssignment work = find(orderId, kind);
    WorkAccess access = access(work, kind, actor(actorId, false));
    if (work == null || !access.visible()) {
      throw new NotFoundException("Sales order not found: " + orderId);
    }
    if (access.assignReason() != null) {
      throw new AccessDeniedException("You may not assign this work");
    }
    UUID tenantId = TenantContext.requireTenantId();
    return users
        .findActiveUserIdsByDepartmentCodes(tenantId, Set.of(work.getDepartmentCode()))
        .stream()
        .filter(userId -> eligible(work, kind, scopes.actor(tenantId, userId, false)))
        .map(userId -> new OrderWorkDtos.Candidate(userId, nameOf(tenantId, userId)))
        .sorted(Comparator.comparing(OrderWorkDtos.Candidate::displayName))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<OrderWorkDtos.EventView> history(UUID orderId, OrderWorkKind kind) {
    return events
        .findByTenantIdAndSalesOrderIdAndKindOrderByOccurredAtDesc(
            TenantContext.requireTenantId(), orderId, kind)
        .stream()
        .map(
            event ->
                new OrderWorkDtos.EventView(
                    event.getType(),
                    event.getDepartmentCode(),
                    event.getFromAssigneeId(),
                    event.getToAssigneeId(),
                    event.getReason(),
                    event.getActorId(),
                    event.getOccurredAt()))
        .toList();
  }

  // ── Views ──────────────────────────────────────────────────────────────

  /** Builds assignment views for one viewer, resolving names and team staffing once per call. */
  public ViewContext viewContext(UUID viewer) {
    UUID tenantId = TenantContext.requireTenantId();
    return new ViewContext(viewer, tenantId);
  }

  public final class ViewContext {
    private final UUID viewer;
    private final UUID tenantId;
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<String, Boolean> staffed = new HashMap<>();

    private ViewContext(UUID viewer, UUID tenantId) {
      this.viewer = viewer;
      this.tenantId = tenantId;
    }

    public UUID viewer() {
      return viewer;
    }

    public OrderWorkDtos.AssignmentView of(OrderWorkAssignment work) {
      if (work == null) {
        return null;
      }
      UUID assignee = work.getAssigneeId();
      boolean takeable =
          work.isAssigned()
              || staffed.computeIfAbsent(
                  work.getKind() + "|" + work.getDepartmentCode(),
                  key -> teamCanTake(work, work.getKind()));
      return new OrderWorkDtos.AssignmentView(
          work.getKind(),
          work.getDepartmentCode(),
          work.getRoutedAt(),
          assignee,
          assignee == null ? null : names.computeIfAbsent(assignee, id -> nameOf(tenantId, id)),
          work.getAssignedAt(),
          work.isAssignedTo(viewer),
          takeable);
    }
  }

  /** CLAIM, ASSIGN and RELEASE on a closed order: only releasing the person stays possible. */
  public static List<OrderWorkDtos.Capability> responsibilityActions(
      WorkAccess access, boolean closed) {
    if (!closed) {
      return responsibilityActions(access);
    }
    return List.of(
        OrderWorkDtos.Capability.of(OrderWorkDtos.Action.CLAIM, "ORDER_CLOSED"),
        OrderWorkDtos.Capability.of(OrderWorkDtos.Action.ASSIGN, "ORDER_CLOSED"),
        OrderWorkDtos.Capability.of(OrderWorkDtos.Action.RELEASE, access.releaseReason()));
  }

  /** CLAIM, ASSIGN and RELEASE of the work for the user. */
  public static List<OrderWorkDtos.Capability> responsibilityActions(WorkAccess access) {
    return List.of(
        OrderWorkDtos.Capability.of(OrderWorkDtos.Action.CLAIM, access.claimReason()),
        OrderWorkDtos.Capability.of(OrderWorkDtos.Action.ASSIGN, access.assignReason()),
        OrderWorkDtos.Capability.of(OrderWorkDtos.Action.RELEASE, access.releaseReason()));
  }

  // ── Internals ──────────────────────────────────────────────────────────

  private boolean eligible(OrderWorkAssignment work, OrderWorkKind kind, WorkActor candidate) {
    DataScope scope = candidate.scope(permissionsOf(kind).work());
    if (scope == null || !users.isActive(TenantContext.requireTenantId(), candidate.userId())) {
      return false;
    }
    return scope == DataScope.ORGANIZATION
        || scope == DataScope.GLOBAL
        || candidate.inDepartment(work.getDepartmentCode());
  }

  /**
   * Work is not taken or assigned on a closed order. The arrival estimate stays open while the
   * goods are on their way; the other work closes once everything is shipped.
   */
  public static boolean closedFor(OrderWorkKind kind, SalesOrder order) {
    return kind == OrderWorkKind.ARRIVAL_ESTIMATE
        ? order.getStatus().isTerminal()
        : order.isClosedForWork();
  }

  private void requireOpen(UUID orderId, OrderWorkKind kind) {
    SalesOrder order =
        orders
            .findByTenantIdAndId(TenantContext.requireTenantId(), orderId)
            .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
    if (closedFor(kind, order)) {
      throw OrderDomainException.stage(
          "ORDER_CLOSED", "Order " + order.getOrderNumber() + " is " + order.getStatus());
    }
  }

  /** Whether an active member of the routed team could take the work. */
  private boolean teamCanTake(OrderWorkAssignment work, OrderWorkKind kind) {
    UUID tenantId = TenantContext.requireTenantId();
    WorkPermissions keys = permissionsOf(kind);
    return users
        .findActiveUserIdsByDepartmentCodes(tenantId, Set.of(work.getDepartmentCode()))
        .stream()
        .map(userId -> scopes.actor(tenantId, userId, false))
        .anyMatch(
            member ->
                member.scope(keys.work()) != null
                    && coversQueue(member.scope(keys.claim()), work, member));
  }

  private OrderWorkAssignment locked(UUID orderId, OrderWorkKind kind) {
    return assignments
        .lockByOrderAndKind(TenantContext.requireTenantId(), orderId, kind)
        .orElseThrow(
            () ->
                OrderDomainException.stage(
                    NOT_ROUTED, "This work has not been routed to a team yet"));
  }

  private String nameOf(UUID tenantId, UUID userId) {
    return users
        .findById(tenantId, userId)
        .map(UserDto::getDisplayName)
        .filter(name -> !name.isBlank())
        .orElse(userId.toString());
  }

  private static void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new OrderDomainException("Say why the responsibility changes");
    }
    if (reason.trim().length() > OrderWorkAssignmentEvent.MAX_REASON_LENGTH) {
      throw new OrderDomainException("The reason is too long");
    }
  }
}
