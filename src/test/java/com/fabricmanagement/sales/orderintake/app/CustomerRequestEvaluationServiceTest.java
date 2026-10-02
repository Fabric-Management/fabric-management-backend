package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestEvaluationOutcome;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestStatus;
import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestEvaluationRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestRevisionRepository;
import com.fabricmanagement.sales.salesorder.app.WorkFixture;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** A custom request on an order is that order's planning work: same scope, same open stage. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerRequestEvaluationServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

  @Mock private CustomerProductRequestRepository requests;
  @Mock private CustomerRequestEvaluationRepository evaluations;
  @Mock private CustomerRequestRevisionRepository revisions;
  @Mock private ProductSalesDefinitionQueryService products;
  @Mock private CustomerRequestViews views;
  @Mock private SalesOrderRepository orders;

  private WorkFixture work;
  private CustomerRequestEvaluationService service;
  private SalesOrder order;
  private CustomerProductRequest onOrder;
  private CustomerProductRequest standalone;
  private UUID planner;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    work = new WorkFixture(Clock.fixed(NOW, ZoneOffset.UTC));
    order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-12")
            .status(OrderStatus.DRAFT)
            .build();
    order.setId(UUID.randomUUID());
    ReflectionTestUtils.setField(order, "tenantId", TENANT);
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.IN_PLANNING);
    work.order(order);
    planner = work.planner();
    work.service.route(order.getId(), OrderWorkKind.PLANNING, null);
    work.service.claim(order.getId(), OrderWorkKind.PLANNING, planner);

    onOrder = request(order.getId());
    standalone = request(null);
    when(orders.lockByTenantIdAndId(TENANT, order.getId())).thenReturn(Optional.of(order));
    when(orders.findAllById(any())).thenReturn(List.of(order));
    when(requests.findByTenantIdAndIdAndIsActiveTrue(TENANT, onOrder.getId()))
        .thenReturn(Optional.of(onOrder));
    when(requests.findByTenantIdAndIdAndIsActiveTrue(TENANT, standalone.getId()))
        .thenReturn(Optional.of(standalone));
    when(requests.findByTenantIdAndStatusInAndIsActiveTrueOrderByRecordedAtAscIdAsc(any(), any()))
        .thenReturn(List.of(onOrder, standalone));
    when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    service =
        new CustomerRequestEvaluationService(
            requests,
            evaluations,
            revisions,
            products,
            views,
            work.service,
            orders,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void anotherPlannerCannotEvaluateARequestOnAnOrderTheyDoNotHold() {
    UUID other = work.planner();

    assertThatThrownBy(() -> service.evaluate(onOrder.getId(), evaluation(), other))
        .isInstanceOf(OrderIntakeException.class);
    assertThat(service.queue(other))
        .extracting(item -> item.orderNumber())
        .containsExactly((String) null);
    verify(evaluations, never()).save(any());
  }

  @Test
  void theResponsiblePlannerEvaluatesWhileTheEvaluationIsOpen() {
    service.evaluate(onOrder.getId(), evaluation(), planner);
    verify(evaluations).save(any());

    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.PLANNED);
    assertThatThrownBy(() -> service.evaluate(onOrder.getId(), evaluation(), planner))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("EVALUATION_CLOSED");
    var item =
        service.queue(planner).stream()
            .filter(value -> "SO-12".equals(value.orderNumber()))
            .findFirst()
            .orElseThrow();
    assertThat(item.actions())
        .allSatisfy(capability -> assertThat(capability.reason()).isEqualTo("EVALUATION_CLOSED"));
  }

  @Test
  void aRequestOnNoOrderNeedsOnlyTheProductionWritePermission() {
    UUID reader =
        work.user("PLANNING", Map.of(PermissionKey.PRODUCTION_READ, DataScope.ORGANIZATION));

    service.evaluate(standalone.getId(), evaluation(), planner);
    verify(evaluations).save(any());
    assertThat(service.queue(reader))
        .filteredOn(item -> item.orderNumber() == null)
        .singleElement()
        .satisfies(
            item ->
                assertThat(item.actions())
                    .allSatisfy(
                        capability ->
                            assertThat(capability.reason()).isEqualTo("PERMISSION_DENIED")));
  }

  private static CustomerRequestDtos.EvaluateRequest evaluation() {
    return new CustomerRequestDtos.EvaluateRequest(
        CustomerRequestEvaluationOutcome.MATCH_EXISTING, "Twill 240 in stock range");
  }

  private static CustomerProductRequest request(UUID orderId) {
    CustomerProductRequest request =
        CustomerProductRequest.record(
            UUID.randomUUID(),
            orderId,
            new CustomerProductRequest.Details(
                "Softer hand", null, null, null, null, null, null, null, null, null),
            false,
            UUID.randomUUID(),
            NOW);
    request.setId(UUID.randomUUID());
    assertThat(request.getStatus()).isEqualTo(CustomerRequestStatus.OPEN);
    return request;
  }
}
