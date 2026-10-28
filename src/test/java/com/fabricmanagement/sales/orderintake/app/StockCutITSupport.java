package com.fabricmanagement.sales.orderintake.app;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.production.core.inventory.domain.InventoryBalance;
import com.fabricmanagement.production.core.inventory.infra.repository.InventoryBalanceRepository;
import com.fabricmanagement.production.core.stockunit.api.*;
import com.fabricmanagement.production.core.stockunit.app.*;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

abstract class StockCutITSupport extends MetreOrderITSupport {
  @Autowired StockUnitCutSessionService cutSessions;
  @Autowired StockUnitCutPlanService cutPlans;
  @Autowired StockUnitRepository stockUnits;
  @Autowired StockUnitCutService legacyCuts;
  @Autowired SalesOrderRepository orderRows;
  @Autowired SalesOrderLineRepository lineRows;
  @Autowired ConfirmationGate confirmation;
  @Autowired PieceAllocationPort allocations;
  @Autowired StockUnitSoftHoldPort holds;
  @Autowired InventoryBalanceRepository balances;
  @MockitoBean PermissionEvaluator cutPermissions;

  @BeforeEach
  void prepareCutPermissionsAndOpeningInventory() {
    when(users.findPermissionIdentity(any(), any()))
        .thenReturn(Optional.of(new UserQueryService.PermissionIdentity("ADMIN", List.of())));
    when(cutPermissions.evaluateFresh(any(), any(), anyList(), any()))
        .thenReturn(new PermissionResult(Map.of(), true));
    in(
        tenant,
        () -> {
          var balance = InventoryBalance.create(tenant, batch, null, "M");
          balance.setQuantity(new BigDecimal("3000"));
          balances.saveAndFlush(balance);
          return null;
        });
  }

  SalesOrderDto createExact() {
    return in(tenant, () -> orders.createOrder(metreOrder(SalesQuantityPolicy.EXACT)));
  }

  StockUnitCutSessionDtos.View openSources(SalesOrderDto order) {
    return in(
        tenant,
        () ->
            cutSessions.open(
                new StockUnitCutSessionDtos.Open(
                    StockUnitCutTargetPort.TargetType.SALES_ORDER,
                    order.getId(),
                    order.getLines().getFirst().getId()),
                actor));
  }

  StockUnitCutSessionDtos.View pick(StockUnitCutSessionDtos.View open) {
    return in(
        tenant,
        () ->
            cutSessions.pick(
                open.selectionId(),
                new StockUnitCutSessionDtos.Pick(
                    open.revision(), List.copyOf(rolls.subList(0, 17)), rolls.get(16)),
                actor));
  }

  StockUnitCutSessionDtos.Apply applyRequest(StockUnitCutSessionDtos.View held) {
    return new StockUnitCutSessionDtos.Apply(
        UUID.randomUUID(),
        held.revision(),
        held.documentVersion(),
        held.grants().stream().map(StockUnitCutSessionDtos.Grant::token).toList());
  }

  StockUnitCutPlanDtos.View apply(StockUnitCutSessionDtos.View held) {
    var request = applyRequest(held);
    return in(tenant, () -> cutSessions.apply(held.selectionId(), request, actor));
  }

  void releaseForExecution(SalesOrderDto order) {
    in(
        tenant,
        () -> {
          var stored = orderRows.findByTenantIdAndId(tenant, order.getId()).orElseThrow();
          var lines =
              lineRows.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
                  tenant, order.getId());
          confirmation.allocate(stored, lines, actor);
          // The confirmed fixture represents the existing customer/production release workflow.
          stored.setStatus(OrderStatus.CONFIRMED);
          orderRows.saveAndFlush(stored);
          return null;
        });
  }

  StockUnitCutPlanDtos.View command(
      StockUnitCutPlanDtos.View plan,
      StockUnitCutPlanDtos.Action action,
      StockUnitCutPlanDtos.Actual actual) {
    return in(
        tenant,
        () ->
            cutPlans.command(
                plan.planId(),
                action,
                new StockUnitCutPlanDtos.Command(
                    UUID.randomUUID(), plan.version(), actual, "Warehouse measurement checked"),
                actor));
  }

  StockUnitCutPlanDtos.Actual measured() {
    return new StockUnitCutPlanDtos.Actual(
        new BigDecimal("50"),
        new BigDecimal("75"),
        BigDecimal.ZERO,
        new BigDecimal("12"),
        new BigDecimal("18"),
        BigDecimal.ZERO,
        null);
  }
}
