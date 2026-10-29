package com.fabricmanagement.sales.orderintake.app;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.orderintake.dto.*;
import com.fabricmanagement.sales.pricing.app.DiscountPolicyService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService;
import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.quote.app.QuoteRollSelectionService;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.dto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

abstract class MetreOrderITSupport extends DraftStockSelectionITSupport {
  @Autowired SalesOrderService orders;
  @Autowired QuoteRepository quotes;
  @Autowired QuoteRollSelectionService quoteCommands;
  @Autowired SalesStockPlanningService planning;
  @MockitoBean TenantReportingCurrencyPort reportingCurrency;
  @MockitoBean DiscountPolicyService discountPolicies;
  @MockitoBean PricingEngineService pricing;
  @MockitoBean com.fabricmanagement.sales.salesproduct.app.SalesProductService catalogue;

  // Stock integration tests exercise synchronous stock safety, not asynchronous board provisioning.
  @MockitoBean
  com.fabricmanagement.flowboard.generator.app.SalesStockPlanningTaskService taskPlanning;

  UUID customer, quoteId, quoteLineId;

  @BeforeEach
  void seedMetreScenario() {
    customer = UUID.randomUUID();
    quoteId = UUID.randomUUID();
    quoteLineId = UUID.randomUUID();
    when(reportingCurrency.getReportingCurrency(any())).thenReturn("GBP");
    when(catalogue.getActiveByProductId(product))
        .thenReturn(
            new com.fabricmanagement.sales.salesproduct.dto.SalesProductDto(
                UUID.randomUUID(),
                product,
                "Cotton poplin",
                "FABRIC",
                new BigDecimal("8.60"),
                "GBP",
                null,
                null,
                null,
                "{}",
                "[]",
                true,
                tenant,
                null));
    when(discountPolicies.getActivePolicy(any())).thenReturn(mock(DiscountPolicy.class));
    when(pricing.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(
            PricingEngineService.PricingResult.builder()
                .discountRate(BigDecimal.ZERO)
                .profitMargin(BigDecimal.ONE)
                .priceZone(QuotePriceZone.FREE)
                .build());
    UUID registry = UUID.randomUUID();
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "insert into common_company.trading_partner_registry(id,uid,official_name,country)"
                  + " values (?,?, 'Northbridge Cloth Ltd','GBR')",
              registry,
              registry.toString());
          jdbc.update(
              "insert into"
                  + " common_company.common_trading_partner(id,tenant_id,uid,registry_id,partner_type)"
                  + " values (?,?,?,?,'CUSTOMER')",
              customer,
              tenant,
              customer.toString(),
              registry);
          jdbc.update(
              "update production.production_execution_batch set quantity=3000 where id=?", batch);
          jdbc.update(
              "update production.stock_unit set length=125, initial_length=125 where batch_id=?",
              batch);
          for (int index = 12; index < 24; index++) {
            UUID id = UUID.randomUUID();
            rolls.add(id);
            jdbc.update(
                """
                insert into production.stock_unit
                (id,uid,created_at,updated_at,tenant_id,barcode,batch_id,package_type,product_type,initial_weight,current_weight,
                 unit,status,source_type,source_id,quality_disposition,length,length_unit,initial_length,initial_length_unit)
                values (?,?,now(),now(),?,?,?,'ROLL','FABRIC',30,30,'KG','AVAILABLE','PRODUCTION',?,'RELEASED',125,'M',125,'M')
                """,
                id,
                id.toString(),
                tenant,
                id.toString(),
                batch,
                UUID.randomUUID());
          }
          jdbc.update(
              """
              insert into sales.quote (id,uid,tenant_id,quote_number,customer_id,assigned_to_id,module_type,status,valid_until,currency)
              values (?,?,?,?,?,?,'FABRIC','DRAFT',?,'GBP')
              """,
              quoteId,
              quoteId.toString(),
              tenant,
              "Q-NORTHBRIDGE-" + quoteId,
              customer,
              actor,
              LocalDate.of(2027, 1, 1));
          jdbc.update(
              """
              insert into sales.quote_line(id,uid,tenant_id,quote_id,product_id,requested_qty,customer_request_qty,initial_requested_qty,
                unit,list_price,offered_price,discount_rate,price_zone,currency)
              values (?,?,?,?,?,2050,2050,2050,'M',8.60,8.60,0,'FREE','GBP')
              """,
              quoteLineId,
              quoteLineId.toString(),
              tenant,
              quoteId,
              product);
          return null;
        });
  }

  @Override
  OrderIntakeStockPreviewRequest request() {
    return new OrderIntakeStockPreviewRequest(
        product,
        null,
        null,
        null,
        customer,
        new BigDecimal("2050"),
        "M",
        false,
        new BigDecimal("5"),
        new BigDecimal("5"),
        rolls.subList(0, 17));
  }

  DraftStockSelectionDto quoteSession() {
    return in(
        tenant,
        () ->
            service.open(
                new DraftStockSelectionRequests.Open(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    product,
                    null,
                    null,
                    quoteId,
                    quoteLineId),
                actor));
  }

  DraftStockSelectionDto holdQuote(DraftStockSelectionDto session) {
    var preview = in(tenant, () -> service.preview(session.selectionId(), request(), actor));
    var option =
        preview.evaluation().options().stream()
            .filter(
                value ->
                    value.placement()
                        == com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption
                            .OptionPlacement.MANUAL)
            .findFirst()
            .orElseThrow();
    return in(
        tenant,
        () ->
            service.acquire(
                session.selectionId(),
                new DraftStockSelectionRequests.Acquire(
                    UUID.randomUUID(),
                    session.selectionRevision(),
                    request(),
                    option.optionKey(),
                    List.of()),
                actor));
  }

  DraftStockSelectionRequests.Proof proof(DraftStockSelectionDto selection) {
    return new DraftStockSelectionRequests.Proof(
        selection.selectionId(),
        selection.selectionRevision(),
        selection.grants().stream().map(DraftStockSelectionDto.Grant::token).toList(),
        true);
  }

  long quoteVersion() {
    return in(
        tenant,
        () ->
            quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, quoteId).orElseThrow().getVersion());
  }

  CreateSalesOrderRequest metreOrder(SalesQuantityPolicy policy) {
    var request = new CreateSalesOrderRequest();
    request.setPartnerId(customer);
    request.setOrderDate(LocalDate.of(2026, 10, 20));
    request.setIdempotencyKey(UUID.randomUUID());
    request.setLines(
        List.of(
            SalesOrderLineRequest.builder()
                .productId(product)
                .requestedQty(new BigDecimal("2050"))
                .unit("M")
                .currency("GBP")
                .unitPrice(new BigDecimal("8.60"))
                .quantityPolicy(policy)
                .quantityPolicyReason(
                    policy == null ? null : "Customer requires the exact quantity")
                .toleranceUpPct(new BigDecimal("5"))
                .toleranceDownPct(new BigDecimal("5"))
                .build()));
    return request;
  }
}
