package com.fabricmanagement.costing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.CostCalculationService;
import com.fabricmanagement.costing.app.DemoCostingSetupService;
import com.fabricmanagement.costing.app.port.WorkOrderPlanningUpdatePort;
import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.item.CalculationBase;
import com.fabricmanagement.costing.domain.item.CostItemScope;
import com.fabricmanagement.costing.domain.template.CostTemplate;
import com.fabricmanagement.costing.domain.template.CostTemplateItem;
import com.fabricmanagement.costing.infra.exchange.EcbExchangeRateProvider;
import com.fabricmanagement.costing.infra.exchange.TcmbExchangeRateProvider;
import com.fabricmanagement.costing.infra.repository.CostCalculationRepository;
import com.fabricmanagement.costing.infra.repository.CostItemRepository;
import com.fabricmanagement.costing.infra.repository.CostTemplateRepository;
import com.fabricmanagement.costing.infra.repository.PriceListItemRepository;
import com.fabricmanagement.costing.infra.repository.PriceListRepository;
import com.fabricmanagement.costing.integration.support.TestCostDataFactory;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("Demo costing setup gives every demo work-order module a planned cost")
class DemoCostingSetupIntegrationTest extends AbstractIntegrationTest {

  /** Global cost items the demo templates use, with the calculation base V002 seeds for them. */
  private static final Map<String, CalculationBase> GLOBAL_ITEMS =
      Map.of(
          "RAW_PRODUCT", CalculationBase.PER_KG,
          "ENERGY", CalculationBase.PER_KG,
          "LABOR", CalculationBase.PER_HOUR,
          "MACHINE", CalculationBase.PER_HOUR,
          "OVERHEAD", CalculationBase.PERCENTAGE);

  @Autowired private DemoCostingSetupService setupService;
  @Autowired private CostCalculationService costService;
  @Autowired private CostItemRepository costItemRepo;
  @Autowired private CostTemplateRepository templateRepo;
  @Autowired private PriceListRepository priceListRepo;
  @Autowired private PriceListItemRepository priceListItemRepo;
  @Autowired private CostCalculationRepository calcRepo;

  @MockitoBean private TenantReportingCurrencyPort tenantPort;
  @MockitoBean private EcbExchangeRateProvider ecbProvider;
  @MockitoBean private TcmbExchangeRateProvider tcmbProvider;
  @MockitoBean private WorkOrderPlanningUpdatePort planningPort;

  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    when(tenantPort.getReportingCurrency(any())).thenReturn("USD");
    // Other costing tests delete every cost item in teardown; restore the globals if so.
    Set<String> present =
        costItemRepo.findActiveForModule("SPINNING").stream()
            .map(item -> item.getCode())
            .collect(Collectors.toSet());
    GLOBAL_ITEMS.forEach(
        (code, base) -> {
          if (!present.contains(code)) {
            costItemRepo.save(
                TestCostDataFactory.createCostItem(code, code, CostItemScope.GLOBAL, base));
          }
        });
  }

  @AfterEach
  void tearDown() {
    calcRepo.deleteAll();
    templateRepo.deleteAll();
    priceListItemRepo.deleteAll();
    priceListRepo.deleteAll();
    TenantContext.clear();
  }

  @Test
  void everyModuleComputesACompletePlannedCost() {
    assertThat(setupService.seedFor(tenantId, "USD")).isEqualTo(6);

    // 1000 kg spinning: raw 2050 + energy 340 + labour 16×0.015×1000 = 240
    // + machine 22×0.010×1000 = 220 → 2850; overhead 10 % → 285; total 3135.
    CostCalculation spinning = planned("SPINNING");
    assertThat(spinning.getTotalCost()).isEqualByComparingTo("3135.0000");
    assertThat(spinning.getCurrency()).isEqualTo("USD");
    assertThat(spinning.isComplete()).isTrue();

    // 1000 kg dyeing: energy 550 + labour 160 + machine 240 → 950; overhead 12 % → 114.
    assertThat(planned("DYEING").getTotalCost()).isEqualByComparingTo("1064.0000");

    for (String module : List.of("WEAVING", "KNITTING", "FINISHING", "GENERIC")) {
      CostCalculation calculation = planned(module);
      assertThat(calculation.getTotalCost()).as(module).isPositive();
      assertThat(calculation.isComplete()).as(module).isTrue();
    }
  }

  @Test
  void runningAgainChangesNothing() {
    setupService.seedFor(tenantId, "USD");
    long priceLists = priceListRepo.count();
    long templates = templateRepo.count();

    assertThat(setupService.seedFor(tenantId, "USD")).isZero();
    assertThat(priceListRepo.count()).isEqualTo(priceLists);
    assertThat(templateRepo.count()).isEqualTo(templates);
  }

  @Test
  void aModuleTheTenantConfiguredIsLeftAlone() {
    CostTemplate own =
        templateRepo.save(
            CostTemplate.create(
                tenantId,
                "Our weaving costs",
                "WEAVING",
                true,
                List.of(new CostTemplateItem("LABOR", BigDecimal.ONE, true))));

    assertThat(setupService.seedFor(tenantId, "USD")).isEqualTo(5);
    assertThat(templateRepo.findDefault(tenantId, "WEAVING"))
        .get()
        .extracting(CostTemplate::getId)
        .isEqualTo(own.getId());
    assertThat(priceListRepo.findAll()).noneMatch(list -> "WEAVING".equals(list.getModuleType()));
  }

  private CostCalculation planned(String module) {
    return costService.computePlanned(
        tenantId, UUID.randomUUID(), module, UUID.randomUUID(), new BigDecimal("1000"), null);
  }
}
