package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/** Uses the real access policy: a stubbed denial would not prove null/system-context behavior. */
@ExtendWith(MockitoExtension.class)
class SalesOrderMutationScopeServiceTest {

  @Mock private SalesOrderRepository orderRepository;
  @Mock private PermissionEvaluator permissionEvaluator;
  @Mock private UserQueryService userQueryService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID targetUserId = UUID.randomUUID();
  private SalesOrderService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    service =
        new SalesOrderService(
            orderRepository,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new SalesOrderAccessPolicy(
                new SalesAccessScopeResolver(permissionEvaluator, userQueryService)),
            null,
            null);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "confirm", "process", "ship", "deliver", "cancel", "hold", "resume", "revise", "delete"
      })
  void nullUserIsDeniedAtEveryServiceEntry(String mutation) {
    assertDeniedWithoutChangingOrder(mutation, null);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "confirm", "process", "ship", "deliver", "cancel", "hold", "resume", "revise", "delete"
      })
  void systemContextDoesNotTransferAuthorityToOutOfScopeTargetUser(String mutation) {
    TenantContext.setCurrentUserId(SystemUser.ID);
    when(userQueryService.findPermissionIdentity(tenantId, targetUserId))
        .thenReturn(Optional.of(new PermissionIdentity("WORKER", List.of())));
    when(permissionEvaluator.evaluate(tenantId, "WORKER", List.of(), targetUserId))
        .thenReturn(new PermissionResult(Map.of("sales", Map.of("write", DataScope.OWN)), false));

    assertDeniedWithoutChangingOrder(mutation, targetUserId);
  }

  private void assertDeniedWithoutChangingOrder(String mutation, UUID userId) {
    OrderStatus initialStatus =
        switch (mutation) {
          case "process", "ship", "cancel", "hold" -> OrderStatus.CONFIRMED;
          case "deliver" -> OrderStatus.SHIPPED;
          case "resume" -> OrderStatus.ON_HOLD;
          case "revise" -> OrderStatus.REJECTED;
          default -> OrderStatus.DRAFT;
        };
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-SCOPE")
            .totals(OrderTotals.zero("GBP"))
            .status(initialStatus)
            .build();
    order.setId(UUID.randomUUID());
    order.setTenantId(tenantId);
    order.setCreatedBy(UUID.randomUUID());
    if (mutation.equals("resume")) {
      order.setStatusBeforeHold(OrderStatus.CONFIRMED);
    }
    if (mutation.equals("revise")) {
      order.setRejectionReason("Rejected fixture");
    }
    OrderStatus previousStatus = order.getStatusBeforeHold();
    String rejectionReason = order.getRejectionReason();
    when(orderRepository.findByTenantIdAndId(tenantId, order.getId()))
        .thenReturn(Optional.of(order));

    assertThatThrownBy(() -> invoke(mutation, order.getId(), userId))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(order.getStatus()).isEqualTo(initialStatus);
    assertThat(order.getStatusBeforeHold()).isEqualTo(previousStatus);
    assertThat(order.getRejectionReason()).isEqualTo(rejectionReason);
    assertThat(order.getActualDeliveryDate()).isNull();
    assertThat(order.getVersion()).isZero();
    assertThat(order.getIsActive()).isTrue();
    assertThat(order.getDeletedAt()).isNull();
  }

  private void invoke(String mutation, UUID orderId, UUID userId) {
    switch (mutation) {
      case "confirm" -> service.confirmOrder(orderId, userId);
      case "process" -> service.startProcessing(orderId, userId);
      case "ship" -> service.shipOrder(orderId, userId);
      case "deliver" -> service.deliverOrder(orderId, userId, LocalDate.now());
      case "cancel" -> service.cancelOrder(orderId, userId);
      case "hold" -> service.holdOrder(orderId, userId);
      case "resume" -> service.resumeOrder(orderId, userId);
      case "revise" -> service.reviseOrder(orderId, userId);
      case "delete" -> service.deleteOrder(orderId, userId);
      default -> throw new IllegalArgumentException(mutation);
    }
  }
}
