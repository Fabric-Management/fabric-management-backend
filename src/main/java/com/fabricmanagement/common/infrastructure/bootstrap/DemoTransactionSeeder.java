package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Component to seed a deterministic, demo-ready transactional dataset for a tenant. Uses shared
 * template fibers to create sales orders, work orders, and batches. Fully idempotent and
 * tenant-parametric.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoTransactionSeeder {

  private final TradingPartnerService tradingPartnerService;
  private final ProductFacade productFacade;
  private final SalesOrderService salesOrderService;
  private final WorkOrderService workOrderService;
  private final BatchService batchService;
  private final FinanceDemoSeeder financeDemoSeeder;
  private final SalesDemoSeeder salesDemoSeeder;
  private final ProcurementDemoSeeder procurementDemoSeeder;

  @Value("${application.seed.demo-transactions.enabled:false}")
  private boolean enabled;

  private static final String DEMO_CUSTOMER_NAME = "Global Fashion Wear Corp.";
  private static final String DEMO_SO_REFERENCE = "PO-DEMO-2026-001";
  private static final String DEMO_WO_NOTES = "Expedite for new collection";

  /**
   * Seeds demo transactions for the specified tenant.
   *
   * @param tenantId The UUID of the tenant to seed
   */
  public void seedFor(UUID tenantId) {
    if (!enabled) {
      log.info("Demo transactions seeding is disabled via properties.");
      return;
    }

    // Capture previous context to restore safely if called in middle of another process
    UUID previousTenantId = null;
    try {
      previousTenantId = TenantContext.getCurrentTenantIdOrNull();
    } catch (Exception e) {
      // Ignored if tenant id not set
    }

    TenantContext.setCurrentTenantId(tenantId);
    try {
      // 1. Idempotency Check
      List<TradingPartnerDto> existing =
          tradingPartnerService.searchByName(tenantId, DEMO_CUSTOMER_NAME);
      if (!existing.isEmpty()) {
        log.info("Demo transactions already exist for tenant: {}. Skipping.", tenantId);
        seedProcurementDemo(tenantId);
        return;
      }

      log.info("Provisioning demo transactions for tenant: {}", tenantId);

      // 2. Create Demo Customer
      CreateTradingPartnerRequest partnerReq = new CreateTradingPartnerRequest();
      partnerReq.setCompanyName(DEMO_CUSTOMER_NAME);
      partnerReq.setCustomName(DEMO_CUSTOMER_NAME);
      partnerReq.setTaxId("US-GFW-" + tenantId.toString().substring(0, 8));
      partnerReq.setPartnerType(PartnerType.CUSTOMER);
      partnerReq.setCountry("USA");
      TradingPartnerDto customer = tradingPartnerService.createPartner(partnerReq);

      // 2b. Finance dataset (DEMO-1): reporting currency (USD), FX rates, AR/AP invoices +
      // payments.
      // Independent of the fiber-dependent production demo below, so it still runs when no template
      // fibers exist. Reuses the customer created above; same tenant context.
      financeDemoSeeder.seedFor(tenantId);

      // 2c. Sales demo orders (DEMO-2a): CONFIRMED orders so Margin + Backlog views show real data.
      // Runs in its own transaction (REQUIRES_NEW) and is isolated here so a failure in the confirm
      // rule-engine path can never roll back the finance demo above or fail playground init.
      try {
        salesDemoSeeder.seedFor(tenantId);
      } catch (Exception salesEx) {
        log.warn(
            "Sales demo order seeding failed for tenant {} — continuing; finance demo unaffected.",
            tenantId,
            salesEx);
      }

      seedProcurementDemo(tenantId);

      // 3. Production demo (best-effort, isolated): sales order → work order → batch.
      // Pre-existing demo data. Wrapped in its own try/catch so a failure here can never roll back
      // the finance demo (seeded above) or fail playground init.
      try {
        List<ProductDto> fibers =
            productFacade.findByType(TenantContext.TEMPLATE_TENANT_ID, ProductType.FIBER);

        if (fibers.isEmpty()) {
          log.warn("No template fibers found. Skipping production demo for tenant: {}", tenantId);
        } else {
          ProductDto fiber1 = fibers.get(0);
          ProductDto fiber2 = fibers.size() > 1 ? fibers.get(1) : fiber1;

          CreateSalesOrderRequest orderReq = new CreateSalesOrderRequest();
          orderReq.setPartnerId(customer.getId());
          orderReq.setCustomerReference(DEMO_SO_REFERENCE);
          orderReq.setOrderDate(LocalDate.now());
          orderReq.setCurrency("USD");
          orderReq.setNotes("Demo Sales Order for initial evaluation");

          SalesOrderLineRequest line1 =
              SalesOrderLineRequest.builder()
                  .productId(fiber1.getId())
                  .requestedQty(new BigDecimal("1500.00"))
                  .unit("KG")
                  .unitPrice(new BigDecimal("12.50"))
                  .currency("USD")
                  .build();

          SalesOrderLineRequest line2 =
              SalesOrderLineRequest.builder()
                  .productId(fiber2.getId())
                  .requestedQty(new BigDecimal("2000.00"))
                  .unit("KG")
                  .unitPrice(new BigDecimal("14.00"))
                  .currency("USD")
                  .build();

          orderReq.setLines(List.of(line1, line2));
          SalesOrderDto salesOrder = salesOrderService.createOrder(orderReq);

          if (salesOrder.getLines() != null && !salesOrder.getLines().isEmpty()) {
            UUID firstLineId = salesOrder.getLines().get(0).getId();

            CreateWorkOrderRequest woReq =
                CreateWorkOrderRequest.builder()
                    .outputProductId(fiber1.getId())
                    .moduleType(WorkOrderModuleType.SPINNING)
                    .salesOrderLineId(firstLineId)
                    .plannedQty(new BigDecimal("1550.00"))
                    .unit("KG")
                    .deadline(LocalDate.now().plusDays(15))
                    .notes(DEMO_WO_NOTES)
                    .build();
            var workOrder = workOrderService.createWorkOrder(woReq);

            CreateBatchRequest batchReq =
                CreateBatchRequest.builder()
                    .productId(fiber1.getId())
                    .productType(ProductType.FIBER)
                    .batchCode("B-DEMO-" + LocalDate.now().getYear() + "-001")
                    .quantity(new BigDecimal("500.00"))
                    .unit("KG")
                    .sourceType(BatchSourceType.INTERNAL_PRODUCTION)
                    .sourceId(workOrder.id())
                    .remarks("Demo Initial Batch")
                    .build();
            batchService.create(batchReq);
          } else {
            log.warn(
                "Demo sales order returned no lines; skipping work-order/batch for tenant: {}",
                tenantId);
          }
        }
      } catch (Exception prodEx) {
        log.warn(
            "Production demo seeding failed for tenant {} — continuing; finance demo already seeded.",
            tenantId,
            prodEx);
      }

      log.info("Successfully provisioned demo transactions for tenant: {}", tenantId);

    } catch (Exception e) {
      log.error("Failed to seed demo transactions for tenant: {}", tenantId, e);
      throw new RuntimeException("Failed to seed demo transactions", e);
    } finally {
      if (previousTenantId != null) {
        TenantContext.setCurrentTenantId(previousTenantId);
      } else {
        TenantContext.clear();
      }
    }
  }

  private void seedProcurementDemo(UUID tenantId) {
    try {
      procurementDemoSeeder.seedFor(tenantId);
    } catch (Exception procurementEx) {
      log.warn(
          "Procurement demo seeding failed for tenant {} - continuing; other demos unaffected.",
          tenantId,
          procurementEx);
    }
  }
}
