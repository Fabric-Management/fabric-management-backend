package com.fabricmanagement.costing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.CostCalculationService;
import com.fabricmanagement.costing.app.port.WorkOrderPlanningUpdatePort;
import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.calculation.CostCalculationLine;
import com.fabricmanagement.costing.domain.calculation.CostEntityType;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import com.fabricmanagement.costing.domain.event.CostVarianceDetectedEvent;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
@DisplayName("Cost Calculation Pipeline Integration Test")
class CostCalculationIntegrationTest
    extends com.fabricmanagement.testsupport.AbstractIntegrationTest {

  @Autowired private CostCalculationService costService;
  @Autowired private CostItemRepository costItemRepo;
  @Autowired private CostTemplateRepository templateRepo;
  @Autowired private PriceListRepository priceListRepo;
  @Autowired private PriceListItemRepository priceListItemRepo;
  @Autowired private ExchangeRateCacheRepository cacheRepo;
  @Autowired private CostCalculationRepository calcRepo;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private ApplicationEvents events;

  @MockBean private TenantReportingCurrencyPort tenantPort;
  @MockBean private com.fabricmanagement.costing.infra.exchange.EcbExchangeRateProvider ecbProvider;
  @MockBean private TcmbExchangeRateProvider tcmbProvider;
  @MockBean private WorkOrderPlanningUpdatePort planningPort;

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
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.clear();
    events.clear();
  }

  @Test
  @DisplayName("Idempotent Recalculation: Soft-deletes existing stage record and inserts new one")
  void idempotentRecalculation_replacesActiveRecord() {
    UUID tenantId = UUID.randomUUID();
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        tenantId);
    UUID workOrderId = UUID.randomUUID();

    // 1. Minimum Viable Items & Template
    costItemRepo.save(
        TestCostDataFactory.createCostItem(
            "LABOR", "Labor", CostItemScope.GLOBAL, CalculationBase.PER_KG));
    templateRepo.save(
        TestCostDataFactory.createDefaultTemplate(
            tenantId, "KNITTING", List.of(new CostTemplateItem("LABOR", BigDecimal.ONE, true))));
    PriceList pl =
        priceListRepo.save(
            TestCostDataFactory.createPriceListWithItems(tenantId, "KNITTING", "TRY", Map.of()));
    priceListItemRepo.save(
        TestCostDataFactory.createPriceListItem(
            pl.getId(), "LABOR", null, new BigDecimal("15.0"), "TRY"));

    // Action 1: First Calculation (100 KG)
    CostCalculation firstCalc =
        costService.computePlanned(
            tenantId, workOrderId, "KNITTING", null, new BigDecimal("100.0"), null);
    assertThat(firstCalc.getTotalCost()).isEqualByComparingTo("1500.0000"); // 15 * 100

    // Action 2: Recalculate with different Quantity (200 KG)
    CostCalculation secondCalc =
        costService.computePlanned(
            tenantId, workOrderId, "KNITTING", null, new BigDecimal("200.0"), null);
    assertThat(secondCalc.getTotalCost()).isEqualByComparingTo("3000.0000"); // 15 * 200

    assertThat(firstCalc.getId()).isNotEqualTo(secondCalc.getId());

    // Verify DB State (Wait to query until explicit persistence is confirmed. The service
    // guarantees it flush/commits implicitly or via flush if needed.
    // In @SpringBootTest without @Transactional on method, JPA repositories save changes
    // immediately to the test DB container.)
    List<CostCalculation> allPlanned =
        calcRepo.findAll().stream()
            .filter(c -> c.getEntityId().equals(workOrderId) && c.getStage() == CostStage.PLANNED)
            .toList();

    assertThat(allPlanned).hasSize(2);

    // Only one should be active
    long activeCount = allPlanned.stream().filter(CostCalculation::getIsActive).count();
    assertThat(activeCount).isEqualTo(1);

    // The active one should be the second
    CostCalculation activeCalc =
        calcRepo
            .findActiveByEntityTypeAndEntityIdAndStage(
                CostEntityType.WORK_ORDER, workOrderId, CostStage.PLANNED)
            .orElseThrow();
    assertThat(activeCalc.getId()).isEqualTo(secondCalc.getId());
  }

  @Test
  @DisplayName("CostVarianceDetectedEvent emitted when ACTUAL deviates from PLANNED by threshold")
  void varianceDetection_emitsEventIfExceedsThreshold() {
    UUID tenantId = UUID.randomUUID();
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        tenantId);
    UUID workOrderId = UUID.randomUUID(); // Represents the entity ID in PLANNED

    // 1. Items & Template
    costItemRepo.save(
        TestCostDataFactory.createCostItem(
            "ENERGY", "Energy", CostItemScope.GLOBAL, CalculationBase.PER_KG));
    templateRepo.save(
        TestCostDataFactory.createDefaultTemplate(
            tenantId, "DYEING", List.of(new CostTemplateItem("ENERGY", BigDecimal.ONE, true))));
    PriceList pl =
        priceListRepo.save(
            TestCostDataFactory.createPriceListWithItems(tenantId, "DYEING", "TRY", Map.of()));
    priceListItemRepo.save(
        TestCostDataFactory.createPriceListItem(
            pl.getId(), "ENERGY", null, new BigDecimal("20.0"), "TRY"));

    costService.computePlanned(
        tenantId,
        workOrderId,
        "DYEING",
        null,
        new BigDecimal("100.0"), // Planned 100 KG -> 2000 TRY
        null);

    // In CostCalculationService, ACTUAL for WorkOrder searches for the PLANNED record associated
    // with that the same WorkOrder.
    // Action: Actual (120 KG -> 2400 TRY). Variance = (2400-2000)/2000 = 0.20 (20%) -> Should
    // trigger event!
    costService.computeActualForWorkOrderWithConsumptions(
        tenantId,
        workOrderId,
        "DYEING",
        null,
        new BigDecimal("120.0"),
        null,
        java.util.Collections.emptyList());

    // Assert Domain Event filtered by entityId to avoid cross-test pollution
    long varianceEvents =
        events.stream(CostVarianceDetectedEvent.class)
            .filter(e -> e.getEntityId().equals(workOrderId))
            .count();
    assertThat(varianceEvents).isEqualTo(1);

    CostVarianceDetectedEvent event =
        events.stream(CostVarianceDetectedEvent.class)
            .filter(e -> e.getEntityId().equals(workOrderId))
            .findFirst()
            .orElseThrow();
    assertThat(event.getTenantId()).isEqualTo(tenantId);
    assertThat(event.getEntityId()).isEqualTo(workOrderId);
    assertThat(event.getPreviousTotal()).isEqualByComparingTo("2000.0000");
    assertThat(event.getCurrentTotal()).isEqualByComparingTo("2400.0000");
    assertThat(event.getVarianceRatio()).isEqualByComparingTo("0.2000");
  }

  @Test
  @DisplayName("PERCENTAGE base bypasses exchange rate converting")
  void percentageBase_bypassesExchangeRateConversion() {
    UUID tenantId = UUID.randomUUID();
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        tenantId);
    UUID quoteId = UUID.randomUUID();

    // 1. Setup Items
    costItemRepo.save(
        TestCostDataFactory.createCostItem(
            "LABOR", "Labor", CostItemScope.GLOBAL, CalculationBase.PER_KG));
    costItemRepo.save(
        TestCostDataFactory.createCostItem(
            "OVERHEAD", "Overhead", CostItemScope.GLOBAL, CalculationBase.PERCENTAGE));

    // 2. Setup Template (Overhead represents 15% of cost)
    templateRepo.save(
        TestCostDataFactory.createDefaultTemplate(
            tenantId,
            "WEAVING",
            List.of(
                new CostTemplateItem("LABOR", BigDecimal.ONE, true),
                new CostTemplateItem("OVERHEAD", new BigDecimal("0.15"), true) // Weight = 15%
                )));

    // 3. Setup PriceList (Labor in USD, Overhead in TRY)
    PriceList pl =
        priceListRepo.save(
            TestCostDataFactory.createPriceListWithItems(tenantId, "WEAVING", "TRY", Map.of()));
    priceListItemRepo.save(
        TestCostDataFactory.createPriceListItem(
            pl.getId(), "LABOR", null, new BigDecimal("10.0"), "USD"));
    // PriceListItem for overhead shouldn't strictly matter for amount since base is PERCENTAGE, but
    // we need an entry to satisfy price list fetching logic.
    priceListItemRepo.save(
        TestCostDataFactory.createPriceListItem(
            pl.getId(), "OVERHEAD", null, BigDecimal.ONE, "TRY"));

    // Exchange Rate: 1 USD = 30 TRY
    cacheRepo.save(
        TestCostDataFactory.createRate(
            tenantId,
            "USD",
            "TRY",
            new BigDecimal("30.0"),
            LocalDate.now(),
            com.fabricmanagement.costing.domain.exchange.ExchangeRateSource.MANUAL));

    // Action: Estimate (10 KG)
    CostCalculation calc =
        costService.computeEstimated(
            tenantId, quoteId, "WEAVING", null, new BigDecimal("10.0"), null);

    // Verify LABOR
    // 10 KG * 10 USD/KG = 100 USD. 100 USD * 30 = 3000 TRY.
    CostCalculationLine laborLine =
        calc.getLines().stream()
            .filter(l -> l.getCostItemCode().equals("LABOR"))
            .findFirst()
            .orElseThrow();
    assertThat(laborLine.getTotalInBaseCurrency()).isEqualByComparingTo("3000.0000");
    assertThat(laborLine.getConvertedTotal().getExchangeRate()).isEqualByComparingTo("30.0");

    // Verify OVERHEAD
    // 3000 TRY * 0.15 = 450 TRY. It shouldn't convert again (rate=1).
    CostCalculationLine overheadLine =
        calc.getLines().stream()
            .filter(l -> l.getCostItemCode().equals("OVERHEAD"))
            .findFirst()
            .orElseThrow();
    assertThat(overheadLine.getTotalInBaseCurrency()).isEqualByComparingTo("450.0000");
    assertThat(overheadLine.getConvertedTotal().getExchangeRate())
        .isEqualByComparingTo(BigDecimal.ONE);

    assertThat(calc.getTotalCost()).isEqualByComparingTo("3450.0000");
  }

  @Test
  @DisplayName("Missing Exchange Rate aborts calculation and throws 422 HTTP equivalent exception")
  void missingExchangeRate_throwsException() {
    UUID tenantId = UUID.randomUUID();
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        tenantId);
    UUID quoteId = UUID.randomUUID();

    // 1. Items + Template + Price List in USD
    costItemRepo.save(
        TestCostDataFactory.createCostItem(
            "LABOR", "Labor", CostItemScope.GLOBAL, CalculationBase.PER_KG));
    templateRepo.save(
        TestCostDataFactory.createDefaultTemplate(
            tenantId, "YARN", List.of(new CostTemplateItem("LABOR", BigDecimal.ONE, true))));
    PriceList pl =
        priceListRepo.save(
            TestCostDataFactory.createPriceListWithItems(tenantId, "YARN", "TRY", Map.of()));
    priceListItemRepo.save(
        TestCostDataFactory.createPriceListItem(
            pl.getId(), "LABOR", null, new BigDecimal("10.0"), "USD"));

    // Intentionally no cache & mock returns empty
    when(ecbProvider.getRate(any(), any(), any(), any())).thenReturn(java.util.Optional.empty());
    when(tcmbProvider.getRate(any(), any(), any(), any())).thenReturn(java.util.Optional.empty());

    // Action -> Throw Required Exception
    assertThatThrownBy(
            () ->
                costService.computeEstimated(
                    tenantId, quoteId, "YARN", null, new BigDecimal("10.0"), null))
        .isInstanceOf(ExchangeRateRequiredException.class)
        .hasMessageContaining("USD")
        .hasMessageContaining("TRY");
  }
}
