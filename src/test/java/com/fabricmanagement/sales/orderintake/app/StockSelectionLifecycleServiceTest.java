package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.production.core.stockunit.api.*;
import com.fabricmanagement.sales.orderintake.domain.*;
import com.fabricmanagement.sales.orderintake.infra.repository.DraftStockSelectionRepository;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockSelectionLifecycleServiceTest {
  @Mock DraftStockSelectionRepository selections;
  @Mock SalesOrderLineRepository lines;
  @Mock StockUnitSoftHoldPort holds;
  @Mock StockUnitOwnershipPort ownership;
  @Mock ProvisionalStockLifecycleService provisional;
  @Mock com.fabricmanagement.production.core.stockunit.api.StockUnitCutPlanLifecyclePort cutPlans;
  @InjectMocks StockSelectionLifecycleService lifecycle;
  UUID tenant = UUID.randomUUID();
  SalesOrder order;
  SalesOrderLine line;
  DraftStockSelection selected;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenant);
    order = SalesOrder.builder().tradingPartnerId(UUID.randomUUID()).build();
    order.setId(UUID.randomUUID());
    line =
        SalesOrderLine.builder()
            .productId(UUID.randomUUID())
            .unit("M")
            .requestedQty(new BigDecimal("700"))
            .finishedWidth(new BigDecimal("150"))
            .finishedWidthUnit("CM")
            .unitPriceAmount(new BigDecimal("8.60"))
            .currency("GBP")
            .build();
    line.setId(UUID.randomUUID());
    line.applyStockQuantity(new BigDecimal("700"), new BigDecimal("665"));
    selected =
        DraftStockSelection.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            line.getProductId(),
            UUID.randomUUID(),
            UUID.randomUUID());
    selected.transfer(order.getId(), line.getId(), StockSelectionTerms.fingerprint(order, line));
    when(selections.findByTenantIdAndSalesOrderIdAndState(
            tenant, order.getId(), DraftStockSelection.State.TRANSFERRED))
        .thenReturn(List.of(selected));
    when(lines.findByTenantIdAndId(tenant, line.getId())).thenReturn(Optional.of(line));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void aPriceEditKeepsTheSamePhysicalStockChoice() {
    line.updateUnitPrice(Money.of(new BigDecimal("9.20"), "GBP"));
    line.setRequestedQty(new BigDecimal("665.00"));
    lifecycle.releaseChanged(order);
    assertThat(selected.getState()).isEqualTo(DraftStockSelection.State.TRANSFERRED);
    assertThat(line.getRequestedQty()).isEqualByComparingTo("665");
    assertThat(line.getCustomerRequestQty()).isEqualByComparingTo("700");
    assertThat(line.getUnitPriceAmount()).isEqualByComparingTo("9.20");
    verifyNoInteractions(holds, ownership);
  }

  @Test
  void changingWidthReleasesOnlyTheAffectedChoiceWithoutRenegotiatingQuantity() {
    line.setFinishedWidth(new BigDecimal("160"));
    lifecycle.releaseChanged(order);
    assertThat(selected.getState()).isEqualTo(DraftStockSelection.State.RELEASED);
    assertThat(line.getRequestedQty()).isEqualByComparingTo("665");
    assertThat(line.getCustomerRequestQty()).isEqualByComparingTo("700");
    verify(holds).releaseHolds(line.getId());
    verify(ownership).lockProduct(line.getProductId());
  }

  @Test
  void editingTheCustomerRequestPreservesTheNewRequestWhenReleasingOldStock() {
    line.setRequestedQty(new BigDecimal("800"));
    lifecycle.releaseChanged(order);
    assertThat(line.getRequestedQty()).isEqualByComparingTo("800");
    assertThat(line.getCustomerRequestQty()).isEqualByComparingTo("800");
    verify(holds).releaseHolds(line.getId());
  }

  @Test
  void aMaterialEditAlsoReleasesCandidatesShownForASavedLineBeforeApply() {
    var candidate =
        DraftStockSelection.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            line.getProductId(),
            UUID.randomUUID(),
            UUID.randomUUID());
    candidate.bindTarget(order.getId(), line.getId());
    var request =
        new com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest(
            line.getProductId(),
            line.getColorId(),
            line.getFinishedWidth(),
            line.getFinishedWidthUnit(),
            order.getTradingPartnerId(),
            line.getCustomerRequestQty(),
            line.getUnit(),
            false,
            null,
            null,
            null);
    candidate.hold(
        request, null, List.of(UUID.randomUUID()), java.time.Instant.now().plusSeconds(90));
    when(selections.findByTenantIdAndTargetOrderIdAndState(
            tenant, order.getId(), DraftStockSelection.State.HELD))
        .thenReturn(List.of(candidate));
    line.setFinishedWidth(new BigDecimal("160"));
    lifecycle.releaseChanged(order);
    verify(provisional).ownerLine(line.getId());
    assertThat(line.getRequestedQty()).isEqualByComparingTo("665");
  }

  @Test
  void changingCustomerInvalidatesTheSourceDistribution() {
    order.setTradingPartnerId(UUID.randomUUID());
    lifecycle.releaseChanged(order);
    verify(holds).releaseHolds(line.getId());
  }
}
