package com.fabricmanagement.costing.integration.support;

import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.domain.item.CalculationBase;
import com.fabricmanagement.costing.domain.item.CostItem;
import com.fabricmanagement.costing.domain.item.CostItemScope;
import com.fabricmanagement.costing.domain.price.PriceList;
import com.fabricmanagement.costing.domain.price.PriceListItem;
import com.fabricmanagement.costing.domain.template.CostTemplate;
import com.fabricmanagement.costing.domain.template.CostTemplateItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Static factory methods for creating test data objects in integration tests. The created entities
 * are transient and must be saved via JPA repositories.
 */
public class TestCostDataFactory {

  public static CostItem createCostItem(
      String code, String name, CostItemScope scope, CalculationBase base) {
    return CostItem.builder()
        .code(code)
        .name(name)
        .scope(scope)
        .calculationBase(base)
        .displayOrder(0)
        .build();
  }

  public static CostTemplate createDefaultTemplate(
      UUID tenantId, String moduleType, List<CostTemplateItem> items) {
    return CostTemplate.create(
        tenantId, "Default " + moduleType + " Template", moduleType, true, items);
  }

  public static PriceList createPriceListWithItems(
      UUID tenantId, String moduleType, String currency, Map<String, BigDecimal> itemPrices) {

    PriceList pl =
        PriceList.create(
            tenantId,
            "Test Price List (" + moduleType + ")",
            moduleType,
            currency,
            LocalDate.now().minusDays(1),
            null,
            "TEST");
    // Note: Items must be associated and saved explicitly to the PriceListItemRepository
    return pl;
  }

  public static PriceListItem createPriceListItem(
      UUID priceListId,
      String costItemCode,
      UUID materialId,
      BigDecimal unitPrice,
      String currency) {
    return PriceListItem.builder()
        .priceListId(priceListId)
        .costItemCode(costItemCode)
        .materialId(materialId)
        .unitPrice(unitPrice)
        .unit(costItemCode.equals("RAW_MATERIAL") ? "KG" : "UNIT")
        .currency(currency)
        .build();
  }

  public static ExchangeRateCache createRate(
      UUID tenantId,
      String from,
      String to,
      BigDecimal rate,
      LocalDate date,
      com.fabricmanagement.costing.domain.exchange.ExchangeRateSource source) {
    ExchangeRateCache cache =
        ExchangeRateCache.builder()
            .baseCurrency(from)
            .targetCurrency(to)
            .rateDate(date)
            .rate(rate)
            .build();
    cache.setTenantId(tenantId);
    cache.setSource(source);
    return cache;
  }
}
