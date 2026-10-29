package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.product.core.api.facade.ProductFacade;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.production.core.batch.app.BatchService;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.core.workorder.app.WorkOrderService;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Component to seed a deterministic, demo-ready transactional dataset for a tenant. Uses the shared
 * cotton (CO) and polyester (PES) canonical fibres, looked up by ISO code, to create sales orders,
 * work orders, and batches. Fully idempotent and tenant-parametric. It never installs the
 * playground fibre fixtures (FIBER-CATALOG-1); those belong to initial playground provisioning.
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
  private final SalesQuoteDemoSeeder salesQuoteDemoSeeder;
  private final PlaygroundSeedScopePort playgroundSeedScope;
  private final PartnerNetworkDemoSeeder partnerNetworkDemoSeeder;

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
    seedForInternal(tenantId);
  }

  public void seedIfEnabledFor(UUID tenantId) {
    if (!enabled) {
      log.info("Demo transactions seeding is disabled via properties.");
      return;
    }
    seedForInternal(tenantId);
  }

  private void seedForInternal(UUID tenantId) {
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
        seedSalesQuoteDemo(tenantId);
        seedPartnerNetworkDemo(tenantId);
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
      TradingPartnerDto customer = tradingPartnerService.createPartner(partnerReq, null);

      // 2b. Finance dataset (DEMO-1): reporting currency (USD), FX rates, AR/AP invoices +
      // payments.
      // Independent of the fiber-dependent production demo below, so it still runs when no template
      // fibers exist. Reuses the customer created above; same tenant context.
      financeDemoSeeder.seedFor(tenantId);

      // 2c. Sales demo orders (DEMO-2a): CONFIRMED orders so Margin + Backlog views show real data.
      // Runs in its own transaction (REQUIRES_NEW), after the caller's transaction commits when
      // there is one (it must see the demo customers created above), and is isolated so a failure
      // in the confirm rule-engine path can never roll back the finance demo above or fail
      // playground init.
      seedSalesOrderDemo(tenantId);

      seedProcurementDemo(tenantId);
      seedSalesQuoteDemo(tenantId);
      seedPartnerNetworkDemo(tenantId);

      // 3. Production demo (best-effort, isolated): sales order → work order → batch.
      // Pre-existing demo data. Wrapped in its own try/catch so a failure here can never roll back
      // the finance demo (seeded above) or fail playground init.
      try {
        // FIBER-CATALOG-1: exact shared materials by ISO code (cotton, polyester), never the
        // first entries of a product list.
        Optional<ProductDto> cotton = productFacade.findCanonicalFiberProduct("CO");
        Optional<ProductDto> polyester = productFacade.findCanonicalFiberProduct("PES");

        if (cotton.isEmpty() || polyester.isEmpty()) {
          log.warn(
              "Shared CO/PES fibres not published. Skipping production demo for tenant: {}",
              tenantId);
        } else {
          ProductDto fiber1 = cotton.get();
          ProductDto fiber2 = polyester.get();

          CreateSalesOrderRequest orderReq = new CreateSalesOrderRequest();
          orderReq.setPartnerId(customer.getId());
          orderReq.setCustomerReference(DEMO_SO_REFERENCE);
          orderReq.setOrderDate(LocalDate.now());
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
        // Isolated so it cannot roll back the finance demo, but logged at ERROR: a swallowed
        // warn here hid a foreign-key violation that silently produced zero batches (BATCH-FK-1).
        log.error(
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

  /**
   * The confirmed sales orders (DEMO-2a). They run in their own transaction (REQUIRES_NEW), which
   * cannot see rows the register-first signup has not committed yet: the demo customers, and the
   * tenant itself (ONBOARD-ATOMIC-1). Run inline, the seeder found no customer in every such signup
   * and the playground silently had no sales demo orders. So when a transaction is active the run
   * is deferred to just after its commit, ahead of the colour/stock and partner-network demos
   * (callbacks run in registration order); without one it runs at once.
   */
  private void seedSalesOrderDemo(UUID tenantId) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              runSalesOrderDemo(tenantId);
            }
          });
      return;
    }
    runSalesOrderDemo(tenantId);
  }

  /**
   * Rebinds the tenant before the seeder's own transaction opens (the orchestrator has restored the
   * caller's context by the time {@code afterCommit} runs) and catches everything: an exception
   * escaping an {@code afterCommit} callback propagates out of {@code commit()} and would turn a
   * signup that did commit into an error response.
   */
  private void runSalesOrderDemo(UUID tenantId) {
    TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          try {
            salesDemoSeeder.seedFor(tenantId);
          } catch (Exception salesEx) {
            log.warn(
                "Sales demo order seeding failed for tenant {} — continuing; finance demo"
                    + " unaffected.",
                tenantId,
                salesEx);
          }
        });
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

  /**
   * The colour/stock demo (DEMO-SALES-1) is playground-only: it runs for a tenant that initial
   * provisioning installed playground fixtures for, and for nothing else (FIBER-CATALOG-1 §9).
   *
   * <p>It runs in its own transaction (REQUIRES_NEW) so a failure inside it can never abort the
   * caller's transaction — but a REQUIRES_NEW transaction cannot see rows the caller has not yet
   * committed, and the register-first signup calls this from inside its onboarding transaction with
   * the root organisation, roles and departments still uncommitted. Seeding then failed with "Root
   * organisation missing" and rolled every colour card back. So when a transaction is active the
   * run is deferred to just after that transaction commits (same thread, before the caller
   * returns); without one it runs at once.
   */
  private void seedSalesQuoteDemo(UUID tenantId) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              runSalesQuoteDemo(tenantId);
            }
          });
      return;
    }
    runSalesQuoteDemo(tenantId);
  }

  /**
   * Runs with the tenant bound BEFORE any transaction opens: row-level security binds {@code
   * app.current_tenant} when a transaction acquires its connection, and in the afterCommit path the
   * caller's tenant context has already been restored. Both the scope check and the seeder's own
   * REQUIRES_NEW transaction therefore start inside this context, never before it.
   */
  private void runSalesQuoteDemo(UUID tenantId) {
    TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          try {
            if (!playgroundSeedScope.isInitialPlaygroundProvisioning(tenantId)) {
              log.info(
                  "Sales quote demo skipped for tenant {}: not an initial playground provisioning.",
                  tenantId);
              return;
            }
            salesQuoteDemoSeeder.seedFor(tenantId);
          } catch (Exception salesQuoteEx) {
            log.warn(
                "Sales quote demo seeding failed for tenant {} - continuing; other demos"
                    + " unaffected.",
                tenantId,
                salesQuoteEx);
          }
        });
  }

  /**
   * The people, address cards and linked companies of the demo customers and suppliers
   * (PLAYGROUND-PARTIES-1). Runs after every demo that creates a partner, the colour/stock demo
   * included, so it follows {@link #seedSalesQuoteDemo}: deferred to after the caller's commit when
   * a transaction is active (callbacks run in registration order), at once otherwise. The seeder
   * opens its own transaction, since one joined from an after-commit callback is never committed.
   */
  private void seedPartnerNetworkDemo(UUID tenantId) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              runPartnerNetworkDemo(tenantId);
            }
          });
      return;
    }
    runPartnerNetworkDemo(tenantId);
  }

  private void runPartnerNetworkDemo(UUID tenantId) {
    TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          try {
            partnerNetworkDemoSeeder.seedFor(tenantId);
          } catch (Exception networkEx) {
            log.warn(
                "Partner network demo seeding failed for tenant {} - continuing; other demos"
                    + " unaffected.",
                tenantId,
                networkEx);
          }
        });
  }
}
