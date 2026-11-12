package com.fabricmanagement.costing.app;

import com.fabricmanagement.costing.domain.price.PriceList;
import com.fabricmanagement.costing.domain.price.PriceListItem;
import com.fabricmanagement.costing.domain.template.CostTemplate;
import com.fabricmanagement.costing.domain.template.CostTemplateItem;
import com.fabricmanagement.costing.infra.repository.CostTemplateRepository;
import com.fabricmanagement.costing.infra.repository.PriceListItemRepository;
import com.fabricmanagement.costing.infra.repository.PriceListRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo costing setup: one price list and one default cost template per work-order module, so a demo
 * tenant's approved work orders show a planned cost instead of an empty field.
 *
 * <p>The figures are illustrative standard costs, labelled as demo data in every name. Only the
 * global cost items are used: module-specific items (YARN_*, FABRIC_*, DYE_*) are keyed by a
 * different module vocabulary than work orders use and would never resolve.
 *
 * <p>Idempotent per module: a module that already has a default template or an active price list is
 * left untouched, so a tenant's own configuration is never overwritten.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoCostingSetupService {

  static final String PRICE_LIST_NAME = "Demo standard costs";
  static final String TEMPLATE_NAME = "Demo standard cost template";
  static final String SEASON_TAG = "DEMO";

  private final PriceListRepository priceListRepository;
  private final PriceListItemRepository priceListItemRepository;
  private final CostTemplateRepository costTemplateRepository;
  private final Clock clock;

  /** One template line: cost item, its price, the price unit and the template weight. */
  record Line(String costItemCode, String unitPrice, String unit, String weight) {}

  /** Standard cost lines per work-order module; prices per kg or per hour, weight per kg. */
  static final List<ModuleSetup> MODULES =
      List.of(
          new ModuleSetup(
              "SPINNING",
              List.of(
                  new Line("RAW_PRODUCT", "2.05", "KG", null),
                  new Line("ENERGY", "0.34", "KG", null),
                  new Line("LABOR", "16.00", "HOUR", "0.015"),
                  new Line("MACHINE", "22.00", "HOUR", "0.010"),
                  new Line("OVERHEAD", "0", "PERCENT", "0.10"))),
          new ModuleSetup(
              "WEAVING",
              List.of(
                  new Line("RAW_PRODUCT", "3.20", "KG", null),
                  new Line("ENERGY", "0.28", "KG", null),
                  new Line("LABOR", "16.00", "HOUR", "0.020"),
                  new Line("MACHINE", "24.00", "HOUR", "0.018"),
                  new Line("OVERHEAD", "0", "PERCENT", "0.10"))),
          new ModuleSetup(
              "KNITTING",
              List.of(
                  new Line("RAW_PRODUCT", "3.20", "KG", null),
                  new Line("ENERGY", "0.18", "KG", null),
                  new Line("LABOR", "16.00", "HOUR", "0.012"),
                  new Line("MACHINE", "20.00", "HOUR", "0.012"),
                  new Line("OVERHEAD", "0", "PERCENT", "0.10"))),
          new ModuleSetup(
              "DYEING",
              List.of(
                  new Line("ENERGY", "0.55", "KG", null),
                  new Line("LABOR", "16.00", "HOUR", "0.010"),
                  new Line("MACHINE", "20.00", "HOUR", "0.012"),
                  new Line("OVERHEAD", "0", "PERCENT", "0.12"))),
          new ModuleSetup(
              "FINISHING",
              List.of(
                  new Line("ENERGY", "0.30", "KG", null),
                  new Line("LABOR", "16.00", "HOUR", "0.008"),
                  new Line("MACHINE", "20.00", "HOUR", "0.010"),
                  new Line("OVERHEAD", "0", "PERCENT", "0.10"))),
          new ModuleSetup(
              "GENERIC",
              List.of(
                  new Line("RAW_PRODUCT", "2.00", "KG", null),
                  new Line("OVERHEAD", "0", "PERCENT", "0.08"))));

  record ModuleSetup(String moduleType, List<Line> lines) {}

  /**
   * Seeds the demo costing setup for the tenant bound in {@code TenantContext}.
   *
   * @param tenantId the demo tenant
   * @param currency the tenant's reporting currency, so no conversion is needed
   * @return how many modules were set up (0 when everything already existed)
   */
  @Transactional
  public int seedFor(UUID tenantId, String currency) {
    LocalDate today = LocalDate.now(clock);
    LocalDate validFrom = today.withDayOfYear(1);
    int seeded = 0;
    for (ModuleSetup module : MODULES) {
      boolean configured =
          costTemplateRepository.findDefault(tenantId, module.moduleType()).isPresent()
              || priceListRepository
                  .findActiveForModule(tenantId, module.moduleType(), today)
                  .isPresent();
      if (configured) {
        continue;
      }
      PriceList priceList =
          priceListRepository.save(
              PriceList.create(
                  tenantId,
                  PRICE_LIST_NAME + " — " + module.moduleType(),
                  module.moduleType(),
                  currency,
                  validFrom,
                  null,
                  SEASON_TAG));
      List<CostTemplateItem> templateItems = new ArrayList<>();
      for (Line line : module.lines()) {
        PriceListItem item =
            PriceListItem.builder()
                .priceListId(priceList.getId())
                .costItemCode(line.costItemCode())
                .unitPrice(new BigDecimal(line.unitPrice()))
                .unit(line.unit())
                .currency(currency)
                .build();
        item.setTenantId(tenantId);
        priceListItemRepository.save(item);
        templateItems.add(
            new CostTemplateItem(
                line.costItemCode(),
                line.weight() == null ? null : new BigDecimal(line.weight()),
                true));
      }
      costTemplateRepository.save(
          CostTemplate.create(
              tenantId,
              TEMPLATE_NAME + " — " + module.moduleType(),
              module.moduleType(),
              true,
              templateItems));
      seeded++;
    }
    if (seeded > 0) {
      log.info("Demo costing setup seeded for tenant {}: {} module(s)", tenantId, seeded);
    }
    return seeded;
  }
}
