package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderCapabilityDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderCapabilityDto.Action;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

/** The capabilities the order detail carries say what the commands would accept, and why not. */
class SalesOrderCapabilityServiceTest {

  private final UUID tenantId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final Authentication authentication = mock(Authentication.class);
  private final SalesOrderAccessPolicy accessPolicy = mock(SalesOrderAccessPolicy.class);
  private final SpELPermissionEvaluator permissions = mock(SpELPermissionEvaluator.class);
  private SalesOrderCapabilityService service;

  @BeforeEach
  void setUp() {
    service =
        new SalesOrderCapabilityService(
            mock(SalesOrderRepository.class), accessPolicy, permissions);
    when(accessPolicy.canWrite(eq(tenantId), eq(actor), any())).thenReturn(true);
    when(permissions.can(eq(authentication), eq("sales"), any())).thenReturn(true);
  }

  @Test
  @DisplayName("every action is listed once, with the entity's status rules")
  void draftOrderCapabilities() {
    Map<Action, SalesOrderCapabilityDto> byAction = resolve(order(OrderStatus.DRAFT));

    assertThat(byAction).containsOnlyKeys(Action.values());
    assertThat(allowed(byAction))
        .containsExactlyInAnyOrder(Action.UPDATE, Action.DELETE, Action.HOLD, Action.CANCEL);
    assertThat(byAction.get(Action.SHIP).reason()).isEqualTo(SalesOrderCapabilityDto.WRONG_STATUS);
  }

  @Test
  @DisplayName("a confirmed order is processed, shipped, held or cancelled")
  void confirmedOrderCapabilities() {
    assertThat(allowed(resolve(order(OrderStatus.CONFIRMED))))
        .containsExactlyInAnyOrder(Action.PROCESS, Action.SHIP, Action.HOLD, Action.CANCEL);
  }

  @Test
  @DisplayName("a held order is resumed; a rejected one is revised; a shipped one is delivered")
  void singleTransitionStatuses() {
    assertThat(allowed(resolve(order(OrderStatus.ON_HOLD))))
        .containsExactlyInAnyOrder(Action.RESUME, Action.CANCEL);
    assertThat(allowed(resolve(order(OrderStatus.REJECTED))))
        .containsExactlyInAnyOrder(Action.REVISE);
    assertThat(allowed(resolve(order(OrderStatus.SHIPPED))))
        .containsExactlyInAnyOrder(Action.DELIVER, Action.HOLD);
  }

  @Test
  @DisplayName("a draft with planning is not edited until it is withdrawn")
  void draftWithPlanningIsNotEditable() {
    SalesOrder order = order(OrderStatus.DRAFT);
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.AWAITING_PLANNING);

    SalesOrderCapabilityDto update = resolve(order).get(Action.UPDATE);

    assertThat(update.allowed()).isFalse();
    assertThat(update.reason()).isEqualTo(SalesOrder.WITH_PLANNING);
  }

  @Test
  @DisplayName("each action asks for the permission its endpoint is annotated with")
  void permissionPerAction() {
    when(permissions.can(eq(authentication), eq("sales"), any())).thenReturn(false);
    when(permissions.can(authentication, "sales", "write")).thenReturn(true);

    Map<Action, SalesOrderCapabilityDto> byAction = resolve(order(OrderStatus.CONFIRMED));

    assertThat(byAction.get(Action.PROCESS).allowed()).isTrue();
    assertThat(byAction.get(Action.SHIP).reason())
        .isEqualTo(SalesOrderCapabilityDto.PERMISSION_DENIED);
    assertThat(byAction.get(Action.CANCEL).reason())
        .isEqualTo(SalesOrderCapabilityDto.PERMISSION_DENIED);
    assertThat(SalesOrderCapabilityService.permissionAction(Action.DELETE)).isEqualTo("delete");
  }

  @Test
  @DisplayName("without object access nothing is allowed, whatever the status or permission")
  void noObjectAccessBlocksEverything() {
    when(accessPolicy.canWrite(eq(tenantId), eq(actor), any())).thenReturn(false);

    Map<Action, SalesOrderCapabilityDto> byAction = resolve(order(OrderStatus.CONFIRMED));

    assertThat(allowed(byAction)).isEmpty();
    assertThat(byAction.values())
        .extracting(SalesOrderCapabilityDto::reason)
        .containsOnly(SalesOrderCapabilityDto.NO_OBJECT_ACCESS);
  }

  private Map<Action, SalesOrderCapabilityDto> resolve(SalesOrder order) {
    return service.resolve(tenantId, actor, authentication, order).stream()
        .collect(Collectors.toMap(SalesOrderCapabilityDto::action, Function.identity()));
  }

  private static List<Action> allowed(Map<Action, SalesOrderCapabilityDto> byAction) {
    return byAction.values().stream()
        .filter(SalesOrderCapabilityDto::allowed)
        .map(SalesOrderCapabilityDto::action)
        .toList();
  }

  private static SalesOrder order(OrderStatus status) {
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-1")
            .status(status)
            .flowStage(OrderFlowStage.DRAFT)
            .orderDate(LocalDate.of(2026, 10, 1))
            .build();
    ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(order, "isActive", true);
    return order;
  }
}
