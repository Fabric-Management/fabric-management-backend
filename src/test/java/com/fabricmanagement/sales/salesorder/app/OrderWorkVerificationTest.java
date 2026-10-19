package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.sales.common.app.WorkScopeResolver;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignment;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignmentEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkEventType;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderWorkAssignmentEventRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderWorkAssignmentRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CONSIGNEE-1 S3: the delivery-verification work is opened once, closed when nothing is left to
 * verify and opened again when something is; opening twice or closing twice changes nothing.
 */
@ExtendWith(MockitoExtension.class)
class OrderWorkVerificationTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ORDER = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-16T09:00:00Z");
  private static final OrderWorkKind KIND = OrderWorkKind.DELIVERY_VERIFICATION;

  @Mock private OrderWorkAssignmentRepository assignments;
  @Mock private OrderWorkAssignmentEventRepository events;
  @Mock private WorkScopeResolver scopes;
  @Mock private UserQueryService users;
  @Mock private SalesOrderRepository orders;

  private OrderWorkService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service =
        new OrderWorkService(
            assignments,
            events,
            scopes,
            users,
            orders,
            Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
    org.mockito.Mockito.lenient()
        .when(assignments.save(any(OrderWorkAssignment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private OrderWorkAssignment existing() {
    OrderWorkAssignment work =
        OrderWorkAssignment.route(ORDER, KIND, KIND.defaultDepartment(), NOW);
    when(assignments.lockByOrderAndKind(TENANT, ORDER, KIND)).thenReturn(Optional.of(work));
    return work;
  }

  private OrderWorkEventType onlyEventType() {
    ArgumentCaptor<OrderWorkAssignmentEvent> event =
        ArgumentCaptor.forClass(OrderWorkAssignmentEvent.class);
    verify(events).save(event.capture());
    return event.getValue().getType();
  }

  @Test
  void theWorkIsRoutedToPlanningByDefault() {
    assertThat(KIND.defaultDepartment()).isEqualTo("PLANNING");
  }

  @Test
  void openingWorkThatDoesNotExistRoutesItToThePlanningQueue() {
    when(assignments.lockByOrderAndKind(TENANT, ORDER, KIND)).thenReturn(Optional.empty());

    OrderWorkAssignment work = service.open(ORDER, KIND, ACTOR);

    assertThat(work.getSalesOrderId()).isEqualTo(ORDER);
    assertThat(work.getKind()).isEqualTo(KIND);
    assertThat(work.getDepartmentCode()).isEqualTo("PLANNING");
    assertThat(work.isOpen()).isTrue();
    assertThat(work.isAssigned()).isFalse();
    assertThat(onlyEventType()).isEqualTo(OrderWorkEventType.ROUTED);
  }

  @Test
  void openingWorkThatIsAlreadyOpenChangesNothing() {
    OrderWorkAssignment work = existing();

    OrderWorkAssignment opened = service.open(ORDER, KIND, ACTOR);

    assertThat(opened).isSameAs(work);
    assertThat(work.getRoutedAt()).isEqualTo(NOW);
    verify(assignments, never()).save(any());
    verify(events, never()).save(any());
  }

  @Test
  void closingOpenWorkRecordsWhenAndWhy() {
    OrderWorkAssignment work = existing();

    service.close(ORDER, KIND, ACTOR);

    assertThat(work.isOpen()).isFalse();
    assertThat(work.getClosedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(onlyEventType()).isEqualTo(OrderWorkEventType.CLOSED);
  }

  @Test
  void closingWorkThatIsAbsentOrAlreadyClosedChangesNothing() {
    when(assignments.lockByOrderAndKind(TENANT, ORDER, KIND)).thenReturn(Optional.empty());
    service.close(ORDER, KIND, ACTOR);

    OrderWorkAssignment closed = OrderWorkAssignment.route(ORDER, KIND, "PLANNING", NOW);
    closed.close(NOW);
    when(assignments.lockByOrderAndKind(TENANT, ORDER, KIND)).thenReturn(Optional.of(closed));
    service.close(ORDER, KIND, ACTOR);

    assertThat(closed.getClosedAt()).isEqualTo(NOW);
    verify(assignments, never()).save(any());
    verify(events, never()).save(any());
  }

  @Test
  void openingClosedWorkPutsItBackInTheQueueAsNew() {
    OrderWorkAssignment work = existing();
    work.close(NOW);

    service.open(ORDER, KIND, ACTOR);

    assertThat(work.isOpen()).isTrue();
    assertThat(work.getClosedAt()).isNull();
    assertThat(work.getRoutedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(onlyEventType()).isEqualTo(OrderWorkEventType.ROUTED);
  }

  @Test
  void deliveryVerificationIsGovernedByTheVerifyPermissionAlone() {
    OrderWorkService.WorkPermissions keys = OrderWorkService.permissionsOf(KIND);

    assertThat(keys.read()).isEqualTo(PermissionKey.SALES_VERIFY_DELIVERY);
    assertThat(keys.work()).isEqualTo(PermissionKey.SALES_VERIFY_DELIVERY);
    assertThat(keys.claim()).isEqualTo(PermissionKey.SALES_VERIFY_DELIVERY);
    assertThat(keys.assign()).isEqualTo(PermissionKey.SALES_VERIFY_DELIVERY);
  }
}
