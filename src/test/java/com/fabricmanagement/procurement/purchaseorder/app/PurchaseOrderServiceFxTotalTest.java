package com.fabricmanagement.procurement.purchaseorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DataScopeGuard;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.purchaseorder.app.validation.PurchaseOrderValidationEngine;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.dto.PurchaseOrderResponse;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderLineRepository;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceFxTotalTest {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID PO_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final LocalDate DOC_DATE = LocalDate.of(2026, 6, 30);
  private static final Instant DOC_INSTANT = Instant.parse("2026-06-30T10:00:00Z");

  @Mock private PurchaseOrderRepository poRepository;
  @Mock private PurchaseOrderLineRepository lineRepository;
  @Mock private PurchaseOrderValidationEngine validationEngine;
  @Mock private ApprovalPort approvalPort;
  @Mock private DocumentNumberGenerator documentNumberGenerator;
  @Mock private DataScopeGuard scopeGuard;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private TenantReportingCurrencyPort tenantReportingCurrencyPort;

  private PurchaseOrderService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new PurchaseOrderService(
            poRepository,
            lineRepository,
            validationEngine,
            approvalPort,
            documentNumberGenerator,
            scopeGuard,
            exchangeRateService,
            tenantReportingCurrencyPort);
    when(documentNumberGenerator.generate(
            eq(TENANT_ID), eq("PURCHASE_ORDER"), eq("PO"), any(), eq(5)))
        .thenReturn("PO-20260630-00001");
    when(poRepository.save(any(PurchaseOrder.class)))
        .thenAnswer(
            invocation -> {
              PurchaseOrder po = invocation.getArgument(0);
              po.setId(PO_ID);
              if (po.getCreatedAt() == null) {
                po.setCreatedAt(DOC_INSTANT);
              }
              return po;
            });
    when(lineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createPurchaseOrderConvertsMixedCurrencyLinesIntoHeaderAndReportingCurrency() {
    when(tenantReportingCurrencyPort.getReportingCurrency(TENANT_ID)).thenReturn("GBP");
    when(exchangeRateService.convert(
            eq(TENANT_ID), eq(new BigDecimal("100.000")), eq("USD"), eq("TRY"), eq(DOC_DATE)))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("100.000"),
                "USD",
                new BigDecimal("3000.0000"),
                "TRY",
                new BigDecimal("30.00000000"),
                DOC_DATE));
    when(exchangeRateService.convert(
            eq(TENANT_ID), eq(new BigDecimal("50.000")), eq("EUR"), eq("TRY"), eq(DOC_DATE)))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("50.000"),
                "EUR",
                new BigDecimal("1750.0000"),
                "TRY",
                new BigDecimal("35.00000000"),
                DOC_DATE));
    when(exchangeRateService.convert(
            eq(TENANT_ID), eq(new BigDecimal("4750.0000")), eq("TRY"), eq("GBP"), eq(DOC_DATE)))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("4750.0000"),
                "TRY",
                new BigDecimal("95.0000"),
                "GBP",
                new BigDecimal("0.02000000"),
                DOC_DATE));
    when(scopeGuard.canAccess(eq("procurement"), eq("write"), any(PurchaseOrder.class)))
        .thenReturn(true);

    PurchaseOrderResponse response =
        service.createPurchaseOrder(
            createRequest(
                "TRY", List.of(line("USD", "100.0000", "1.000"), line("EUR", "50.0000", "1.000"))));

    assertThat(response.getTotalAmount()).isEqualByComparingTo("4750.0000");
    assertThat(response.getReportingTotal()).isNotNull();
    assertThat(response.getReportingTotal().getOriginalAmount()).isEqualByComparingTo("4750.0000");
    assertThat(response.getReportingTotal().getOriginalCurrency()).isEqualTo("TRY");
    assertThat(response.getReportingTotal().getConvertedAmount()).isEqualByComparingTo("95.0000");
    assertThat(response.getReportingTotal().getConvertedCurrency()).isEqualTo("GBP");
    assertThat(response.getReportingTotal().getExchangeRate()).isEqualByComparingTo("0.02000000");
    assertThat(response.getReportingTotal().getRateDate()).isEqualTo(DOC_DATE);
  }

  @Test
  void createPurchaseOrderKeepsSingleCurrencyNativeTotalAndPopulatesReportingTotal() {
    when(tenantReportingCurrencyPort.getReportingCurrency(TENANT_ID)).thenReturn("USD");
    when(scopeGuard.canAccess(eq("procurement"), eq("write"), any(PurchaseOrder.class)))
        .thenReturn(true);

    PurchaseOrderResponse response =
        service.createPurchaseOrder(createRequest("USD", List.of(line("USD", "12.5000", "2.000"))));

    assertThat(response.getTotalAmount()).isEqualByComparingTo("25.000");
    assertThat(response.getReportingTotal()).isNotNull();
    assertThat(response.getReportingTotal().getOriginalAmount()).isEqualByComparingTo("25.000");
    assertThat(response.getReportingTotal().getOriginalCurrency()).isEqualTo("USD");
    assertThat(response.getReportingTotal().getConvertedAmount()).isEqualByComparingTo("25.000");
    assertThat(response.getReportingTotal().getConvertedCurrency()).isEqualTo("USD");
    assertThat(response.getReportingTotal().getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(response.getReportingTotal().getRateDate()).isEqualTo(DOC_DATE);
    verify(exchangeRateService, never())
        .convert(
            any(UUID.class),
            any(BigDecimal.class),
            any(String.class),
            any(String.class),
            any(LocalDate.class));
  }

  @Test
  void createPurchaseOrderFailsClosedWhenLineExchangeRateIsMissing() {
    when(exchangeRateService.convert(
            eq(TENANT_ID), eq(new BigDecimal("100.000")), eq("USD"), eq("TRY"), eq(DOC_DATE)))
        .thenThrow(new ExchangeRateRequiredException("USD", "TRY", DOC_DATE));

    assertThatThrownBy(
            () ->
                service.createPurchaseOrder(
                    createRequest("TRY", List.of(line("USD", "100.0000", "1.000")))))
        .isInstanceOf(ProcurementDomainException.class)
        .hasMessageContaining("USD->TRY")
        .hasMessageContaining("2026-06-30");

    verify(poRepository, times(1)).save(any(PurchaseOrder.class));
  }

  private CreatePurchaseOrderRequest createRequest(
      String currency, List<CreatePurchaseOrderRequest.PurchaseOrderLineRequest> lines) {
    return CreatePurchaseOrderRequest.builder()
        .workOrderId(UUID.randomUUID())
        .tradingPartnerId(UUID.randomUUID())
        .currency(currency)
        .paymentTerms("NET30")
        .expectedDelivery(DOC_DATE.plusDays(10))
        .moduleType(PurchaseOrderModuleType.GENERIC)
        .lines(lines)
        .build();
  }

  private CreatePurchaseOrderRequest.PurchaseOrderLineRequest line(
      String currency, String unitPrice, String qty) {
    return CreatePurchaseOrderRequest.PurchaseOrderLineRequest.builder()
        .productDesc("Cotton")
        .qty(new BigDecimal(qty))
        .unit("KG")
        .unitPrice(new BigDecimal(unitPrice))
        .currency(currency)
        .build();
  }
}
