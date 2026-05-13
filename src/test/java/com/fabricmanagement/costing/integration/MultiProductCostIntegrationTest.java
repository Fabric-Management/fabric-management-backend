package com.fabricmanagement.costing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.costing.app.CostCalculationService;
import com.fabricmanagement.costing.app.port.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.port.WorkOrderPlanningUpdatePort;
import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.calculation.CostCalculationLine;
import com.fabricmanagement.costing.domain.item.CalculationBase;
import com.fabricmanagement.costing.domain.item.CostItemScope;
import com.fabricmanagement.costing.domain.price.PriceList;
import com.fabricmanagement.costing.domain.template.CostTemplateItem;
import com.fabricmanagement.costing.infra.exchange.TcmbExchangeRateProvider;
import com.fabricmanagement.costing.infra.repository.CostCalculationRepository;
import com.fabricmanagement.costing.infra.repository.CostItemRepository;
import com.fabricmanagement.costing.infra.repository.CostTemplateRepository;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import com.fabricmanagement.costing.infra.repository.PriceListItemRepository;
import com.fabricmanagement.costing.infra.repository.PriceListRepository;
import com.fabricmanagement.costing.integration.support.TestCostDataFactory;
import com.fabricmanagement.production.execution.workorder.app.port.ConsumptionCostInput;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@DisplayName("Multi-Product Cost Integration Test")
class MultiProductCostIntegrationTest extends AbstractCostingIntegrationTest {

  @Autowired private CostCalculationService costService;
  @Autowired private CostItemRepository costItemRepo;
  @Autowired private CostTemplateRepository templateRepo;
  @Autowired private PriceListRepository priceListRepo;
  @Autowired private PriceListItemRepository priceListItemRepo;
  @Autowired private ExchangeRateCacheRepository cacheRepo;
  @Autowired private CostCalculationRepository calcRepo;

  @MockBean private TenantReportingCurrencyPort tenantPort;
  @MockBean private TcmbExchangeRateProvider tcmbProvider; // Mock external calls
  @MockBean private WorkOrderPlanningUpdatePort planningPort; // Ignore upstream updates

  @BeforeEach
  void setUp() {
    when(tenantPort.getReportingCurrency(any())).thenReturn("TRY");
  }

  @AfterEach
  void tearDown() {
    calcRepo.deleteAll();
    templateRepo.deleteAll();
    priceListItemRepo.deleteAll();
    priceListRepo.deleteAll();
    costItemRepo.deleteAll();
    cacheRepo.deleteAll();
  }

  @Test
  @DisplayName(
      "Per-product pricing with foreign currency translates accurately to reporting currency")
  void consumesMixedProducts_withCurrencyConversion_calculatesCorrectly() {
    UUID tenantId = UUID.randomUUID();
    UUID workOrderId = UUID.randomUUID();
    UUID outputYarnId = UUID.randomUUID();
    UUID fiberAId = UUID.randomUUID();
    UUID fiberBId = UUID.randomUUID();

    // 1. Create items
    costItemRepo.save(
        TestCostDataFactory.createCostItem(
            "RAW_PRODUCT", "Raw Product", CostItemScope.GLOBAL, CalculationBase.PER_KG));
    costItemRepo.save(
        TestCostDataFactory.createCostItem(
            "LABOR", "Labor", CostItemScope.GLOBAL, CalculationBase.PER_KG));

    // 2. Create CostTemplate for YARN
    templateRepo.save(
        TestCostDataFactory.createDefaultTemplate(
            tenantId,
            "YARN",
            List.of(
                new CostTemplateItem("RAW_PRODUCT", BigDecimal.ONE, true),
                new CostTemplateItem("LABOR", BigDecimal.ONE, true))));

    // 3. Create Price Lists
    PriceList plYarn =
        priceListRepo.save(
            TestCostDataFactory.createPriceListWithItems(tenantId, "YARN", "TRY", Map.of()));
    PriceList plFiber =
        priceListRepo.save(
            TestCostDataFactory.createPriceListWithItems(tenantId, "SPINNING", "TRY", Map.of()));

    // Fiber A costs 2.0 USD / KG (Needs conversion). Fiber B costs 55.0 TRY / KG. (Attached to
    // SPINNING)
    priceListItemRepo.save(
        TestCostDataFactory.createPriceListItem(
            plFiber.getId(), "RAW_PRODUCT", fiberAId, new BigDecimal("2.0"), "USD"));
    priceListItemRepo.save(
        TestCostDataFactory.createPriceListItem(
            plFiber.getId(), "RAW_PRODUCT", fiberBId, new BigDecimal("55.0"), "TRY"));

    // Labor costs 10.0 TRY. (Attached to YARN)
    priceListItemRepo.save(
        TestCostDataFactory.createPriceListItem(
            plYarn.getId(), "LABOR", null, new BigDecimal("10.0"), "TRY"));

    // 4. Seed Exchange Rate: USD -> TRY = 38.50
    cacheRepo.save(
        TestCostDataFactory.createRate(
            tenantId,
            "USD",
            "TRY",
            new BigDecimal("38.50"),
            LocalDate.now(),
            com.fabricmanagement.costing.domain.exchange.ExchangeRateSource.MANUAL));

    // 5. Build Consumptions: 60kg FiberA, 40kg FiberB, Net Output = 95kg
    List<ConsumptionCostInput> consumptions =
        List.of(
            new ConsumptionCostInput(
                fiberAId, WorkOrderModuleType.SPINNING, new BigDecimal("60.0"), "KG"),
            new ConsumptionCostInput(
                fiberBId, WorkOrderModuleType.SPINNING, new BigDecimal("40.0"), "KG"));

    // ACTION
    CostCalculation result =
        costService.computeActualForWorkOrderWithConsumptions(
            tenantId,
            workOrderId,
            "YARN",
            outputYarnId,
            new BigDecimal("95.0"),
            null,
            consumptions);

    // ASSERTS
    assertThat(result.getCurrency()).isEqualTo("TRY");
    assertThat(result.getLines()).hasSize(3); // 2 Raw Product paths + 1 Labor

    // Labor: 95kg output * 10.0 TRY/kg = 950.0 TRY
    CostCalculationLine laborLine = findLine(result, "LABOR", null);
    assertThat(laborLine.getConvertedTotal().getOriginalAmount()).isEqualByComparingTo("950.0000");
    assertThat(laborLine.getTotalInBaseCurrency()).isEqualByComparingTo("950.0000");

    // Fiber B: 40kg * 55 TRY/kg = 2200 TRY
    CostCalculationLine fiberBLine = findLine(result, "RAW_PRODUCT", fiberBId);
    assertThat(fiberBLine.getConvertedTotal().getOriginalAmount())
        .isEqualByComparingTo("2200.0000");
    assertThat(fiberBLine.getConvertedTotal().getOriginalCurrency()).isEqualTo("TRY");
    assertThat(fiberBLine.getTotalInBaseCurrency()).isEqualByComparingTo("2200.0000");

    // Fiber A: 60kg * 2.0 USD/kg = 120 USD. 120 USD * 38.50 TRY/USD = 4620 TRY
    CostCalculationLine fiberALine = findLine(result, "RAW_PRODUCT", fiberAId);
    assertThat(fiberALine.getConvertedTotal().getOriginalAmount())
        .isEqualByComparingTo("120.0000"); // Original 120 USD
    assertThat(fiberALine.getConvertedTotal().getOriginalCurrency()).isEqualTo("USD");
    assertThat(fiberALine.getConvertedTotal().getExchangeRate()).isEqualByComparingTo("38.50");

    // Validate Rounding scale in ConvertedTotal object
    assertThat(fiberALine.getConvertedTotal().getConvertedAmount())
        .isEqualByComparingTo("4620.0000");
    assertThat(fiberALine.getTotalInBaseCurrency()).isEqualByComparingTo("4620.0000");

    // Total Cost = 950 + 2200 + 4620 = 7770
    assertThat(result.getTotalCost()).isEqualByComparingTo("7770.0000");
  }

  private CostCalculationLine findLine(
      CostCalculation calc, String expectedCode, UUID expectedProduct) {
    return calc.getLines().stream()
        .filter(l -> l.getCostItemCode().equals(expectedCode))
        .filter(l -> expectedProduct == null || expectedProduct.equals(l.getProductId()))
        .findFirst()
        .orElseThrow();
  }
}
