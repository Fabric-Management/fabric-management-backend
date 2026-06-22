package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds CONFIRMED sales orders for the demo customers (DEMO-2a) so the Profitability/Margin view
 * lists orders with real revenue and the Revenue-&-Backlog trends view shows open-order backlog.
 * Cost estimates (real margin %, DEMO-2b) require the quote → catalog → pricing → cost-calculation
 * chain and are intentionally out of scope here.
 *
 * <p>Runs in its OWN transaction ({@link Propagation#REQUIRES_NEW}) and is invoked from {@link
 * DemoTransactionSeeder} inside a try/catch, so any failure here (e.g. the confirm rule-engine
 * path) can never roll back the finance demo or fail playground init — worst case the
 * margin/backlog views stay empty while every other view keeps its real data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SalesDemoSeeder {

  private final TradingPartnerService tradingPartnerService;
  private final ProductFacade productFacade;
  private final SalesOrderService salesOrderService;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void seedFor(UUID tenantId) {
    List<ProductDto> fibers =
        productFacade.findByType(TenantContext.TEMPLATE_TENANT_ID, ProductType.FIBER);
    if (fibers.isEmpty()) {
      log.warn("No template fibers found; skipping sales demo orders for tenant: {}", tenantId);
      return;
    }
    ProductDto fiber1 = fibers.get(0);
    ProductDto fiber2 = fibers.size() > 1 ? fibers.get(1) : fiber1;

    UUID cAnadolu = findPartner(tenantId, FinanceDemoSeeder.CUSTOMER_ANADOLU);
    UUID cEuropa = findPartner(tenantId, FinanceDemoSeeder.CUSTOMER_EUROPA);
    UUID cGlobal = findPartner(tenantId, FinanceDemoSeeder.CUSTOMER_GLOBAL);

    LocalDate today = LocalDate.now(clock);

    confirmedOrder(cAnadolu, "TRY", "SO-DEMO-ANA-001", today, fiber1, "1000", "320.00");
    confirmedOrder(cEuropa, "EUR", "SO-DEMO-EUR-001", today, fiber1, "500", "100.00");
    confirmedOrder(cGlobal, "USD", "SO-DEMO-GFW-001", today, fiber2, "800", "50.00");

    log.info("Successfully provisioned sales demo orders for tenant: {}", tenantId);
  }

  private UUID findPartner(UUID tenantId, String name) {
    List<TradingPartnerDto> matches = tradingPartnerService.searchByName(tenantId, name);
    if (matches.isEmpty()) {
      throw new IllegalStateException("Demo customer not found for sales order seeding: " + name);
    }
    return matches.get(0).getId();
  }

  private void confirmedOrder(
      UUID partnerId,
      String currency,
      String reference,
      LocalDate orderDate,
      ProductDto product,
      String qty,
      String unitPrice) {
    CreateSalesOrderRequest req = new CreateSalesOrderRequest();
    req.setPartnerId(partnerId);
    req.setCustomerReference(reference);
    req.setOrderDate(orderDate);
    req.setCurrency(currency);
    req.setNotes("Demo seeded sales order");
    req.setLines(
        List.of(
            SalesOrderLineRequest.builder()
                .productId(product.getId())
                .requestedQty(new BigDecimal(qty))
                .unit("KG")
                .unitPrice(new BigDecimal(unitPrice))
                .currency(currency)
                .build()));

    SalesOrderDto order = salesOrderService.createOrder(req);
    // DRAFT → CONFIRMED so the order is picked up by analytics (margin + backlog). In a fresh
    // playground tenant there is no approval policy, so confirmOrder confirms directly.
    salesOrderService.confirmOrder(order.getId());
  }
}
