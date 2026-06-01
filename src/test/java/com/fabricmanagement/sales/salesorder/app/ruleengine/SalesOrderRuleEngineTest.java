package com.fabricmanagement.sales.salesorder.app.ruleengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.port.DraftProductionOrderCommand;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SalesOrderRuleEngineTest {

  @Mock private SalesOrderLineRepository lineRepository;

  @Mock private ProductionOrderPort productionOrderPort;

  @Mock private WorkOrderRecipeHistoryQuery historyQuery;

  @Captor private ArgumentCaptor<DraftProductionOrderCommand> cmdCaptor;

  private SalesOrderRuleEngine ruleEngine;

  private SalesOrder order;
  private SalesOrderLine line;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final UUID lineId = UUID.randomUUID();
  private final UUID partnerId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID recipeId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    ruleEngine = new SalesOrderRuleEngine(lineRepository, productionOrderPort, historyQuery);

    order = SalesOrder.builder().tradingPartnerId(partnerId).build();
    ReflectionTestUtils.setField(order, "id", orderId);

    line = SalesOrderLine.builder().productId(productId).moduleType(ModuleType.FIBER).build();
    ReflectionTestUtils.setField(line, "id", lineId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void processConfirmedOrder_withCertificationAndOrigin_shouldPassToQueryAndCommand() {
    line.setModuleSpecs(Map.of("certificationReq", "GOTS", "originReq", "TR"));

    when(lineRepository.findBySalesOrderIdAndLineStatusAndIsActiveTrue(
            orderId, SalesOrderLineStatus.PENDING))
        .thenReturn(List.of(line));

    when(historyQuery.findDefaultRecipeForProduct(tenantId, productId, "GOTS", "TR"))
        .thenReturn(Optional.of(recipeId));

    ruleEngine.processConfirmedOrder(order);

    verify(lineRepository).save(line);
    assertThat(line.getRecipeId()).isEqualTo(recipeId);

    verify(productionOrderPort).requestDraftProductionOrder(cmdCaptor.capture());
    DraftProductionOrderCommand cmd = cmdCaptor.getValue();
    assertThat(cmd.recipeId()).isEqualTo(recipeId);
    assertThat(cmd.certificationReq()).isEqualTo("GOTS");
    assertThat(cmd.originReq()).isEqualTo("TR");
  }

  @Test
  void processConfirmedOrder_whenNoMatch_shouldCascadeToNextSteps() {
    line.setModuleSpecs(Map.of("certificationReq", "OEKO-TEX"));

    when(lineRepository.findBySalesOrderIdAndLineStatusAndIsActiveTrue(
            orderId, SalesOrderLineStatus.PENDING))
        .thenReturn(List.of(line));

    // Step 1 fails
    when(historyQuery.findDefaultRecipeForProduct(tenantId, productId, "OEKO-TEX", null))
        .thenReturn(Optional.empty());

    // Step 2 fails
    when(historyQuery.findMostRecentRecipeForCustomerAndProduct(
            tenantId, partnerId, productId, "OEKO-TEX", null))
        .thenReturn(Optional.empty());

    // Step 3 matches
    when(historyQuery.findMostUsedRecipeForProduct(tenantId, productId, "OEKO-TEX", null))
        .thenReturn(Optional.of(recipeId));

    ruleEngine.processConfirmedOrder(order);

    verify(lineRepository).save(line);
    assertThat(line.getRecipeId()).isEqualTo(recipeId);
  }

  @Test
  void processConfirmedOrder_whenNoRecipeFound_shouldCreateDraftCommandWithoutRecipe() {
    line.setModuleSpecs(Map.of("certificationReq", "GOTS"));

    when(lineRepository.findBySalesOrderIdAndLineStatusAndIsActiveTrue(
            orderId, SalesOrderLineStatus.PENDING))
        .thenReturn(List.of(line));

    when(historyQuery.findDefaultRecipeForProduct(tenantId, productId, "GOTS", null))
        .thenReturn(Optional.empty());
    when(historyQuery.findMostRecentRecipeForCustomerAndProduct(
            tenantId, partnerId, productId, "GOTS", null))
        .thenReturn(Optional.empty());
    when(historyQuery.findMostUsedRecipeForProduct(tenantId, productId, "GOTS", null))
        .thenReturn(Optional.empty());

    ruleEngine.processConfirmedOrder(order);

    // No recipe assigned
    assertThat(line.getRecipeId()).isNull();

    // Draft still created
    verify(productionOrderPort).requestDraftProductionOrder(cmdCaptor.capture());
    DraftProductionOrderCommand cmd = cmdCaptor.getValue();
    assertThat(cmd.recipeId()).isNull();
    assertThat(cmd.certificationReq()).isEqualTo("GOTS");
  }

  @Test
  void processConfirmedOrder_withEmptySpecs_shouldPassNulls() {
    when(lineRepository.findBySalesOrderIdAndLineStatusAndIsActiveTrue(
            orderId, SalesOrderLineStatus.PENDING))
        .thenReturn(List.of(line));

    when(historyQuery.findDefaultRecipeForProduct(tenantId, productId, null, null))
        .thenReturn(Optional.of(recipeId));

    ruleEngine.processConfirmedOrder(order);

    verify(productionOrderPort).requestDraftProductionOrder(cmdCaptor.capture());
    DraftProductionOrderCommand cmd = cmdCaptor.getValue();
    assertThat(cmd.certificationReq()).isNull();
    assertThat(cmd.originReq()).isNull();
  }

  @Test
  void extractSpecField_shouldNormalizeToUpperCase() {
    // lowercase input should be normalized to uppercase
    line.setModuleSpecs(Map.of("certificationReq", "gots", "originReq", " tr "));

    when(lineRepository.findBySalesOrderIdAndLineStatusAndIsActiveTrue(
            orderId, SalesOrderLineStatus.PENDING))
        .thenReturn(List.of(line));

    when(historyQuery.findDefaultRecipeForProduct(tenantId, productId, "GOTS", "TR"))
        .thenReturn(Optional.of(recipeId));

    ruleEngine.processConfirmedOrder(order);

    verify(productionOrderPort).requestDraftProductionOrder(cmdCaptor.capture());
    DraftProductionOrderCommand cmd = cmdCaptor.getValue();
    assertThat(cmd.certificationReq()).isEqualTo("GOTS");
    assertThat(cmd.originReq()).isEqualTo("TR");
  }
}
