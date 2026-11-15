package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.app.port.PaymentPlanChequeTermsPort;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanInput;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Frequency;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Preview;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentPlanPreviewServiceTest {
  private final UUID tenant = UUID.randomUUID();

  @BeforeEach
  void tenant() {
    TenantContext.setCurrentTenantId(tenant);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private static PaymentPlanChequeTermsPort terms(boolean tracked, int defaultOwn) {
    return new PaymentPlanChequeTermsPort() {
      @Override
      public boolean chequeSplitTracked(UUID tenantId) {
        return tracked;
      }

      @Override
      public int defaultOwnChequePercent(UUID tenantId, UUID partnerId) {
        return defaultOwn;
      }
    };
  }

  @Test
  void previewUsesBackendGrandTotalIncludingDiscountAndTax() {
    try (var validation = Validation.buildDefaultValidatorFactory()) {
      var service = new PaymentPlanPreviewService(validation.getValidator(), terms(false, 100));
      var line = new SalesOrderLineRequest();
      line.setProductId(UUID.randomUUID());
      line.setRequestedQty(new BigDecimal("10"));
      line.setUnit("M");
      line.setUnitPrice(new BigDecimal("100"));
      line.setCurrency("GBP");
      line.setDiscountAmount(new BigDecimal("100"));
      line.setTaxAmount(new BigDecimal("180"));
      line.setToleranceUpPct(new BigDecimal("5"));
      line.setToleranceDownPct(new BigDecimal("5"));
      line.setModuleType(ModuleType.FABRIC);
      var rules =
          new PaymentPlanInput.Rules(
              Frequency.MONTH,
              3,
              LocalDate.of(2026, 10, 30),
              20,
              Method.CASH,
              null,
              null,
              null,
              null,
              null,
              null);
      var plan =
          new PaymentPlanInput(List.of(new PaymentPlanInput.CurrencyInput("GBP", rules, null)));
      var result =
          service
              .preview(
                  new Preview(UUID.randomUUID(), LocalDate.of(2026, 10, 20), List.of(line), plan))
              .content();
      assertThat(result.ready()).isTrue();
      assertThat(result.currencyGroups().getFirst().baseAmount()).isEqualByComparingTo("1080.00");
      assertThat(result.currencyGroups().getFirst().lines())
          .allSatisfy(row -> assertThat(row.amount()).isEqualByComparingTo("360.00"));
      assertThat(result.pricingFingerprint()).hasSize(64);
    }
  }

  /** PAYMENT-PLAN-7 R7: the default reaches the form even while the split issue is open. */
  @Test
  void previewCarriesTheTenantSwitchAndCustomerDefaultBesideAMissingSplit() {
    try (var validation = Validation.buildDefaultValidatorFactory()) {
      var service = new PaymentPlanPreviewService(validation.getValidator(), terms(true, 50));
      var line = new SalesOrderLineRequest();
      line.setProductId(UUID.randomUUID());
      line.setRequestedQty(new BigDecimal("10"));
      line.setUnit("M");
      line.setUnitPrice(new BigDecimal("10"));
      line.setCurrency("GBP");
      line.setToleranceUpPct(new BigDecimal("5"));
      line.setToleranceDownPct(new BigDecimal("5"));
      line.setModuleType(ModuleType.FABRIC);
      var plan =
          new PaymentPlanInput(
              List.of(
                  new PaymentPlanInput.CurrencyInput(
                      "GBP",
                      null,
                      List.of(
                          new PaymentPlanInput.Row(
                              null,
                              Kind.INSTALMENT,
                              new BigDecimal("100.00"),
                              Method.CHEQUE,
                              DateTrigger.EXPLICIT_DATE,
                              LocalDate.of(2026, 11, 30),
                              LocalDate.of(2026, 12, 31))))));
      var result =
          service.preview(
              new Preview(UUID.randomUUID(), LocalDate.of(2026, 10, 20), List.of(line), plan));
      assertThat(result.chequeSplitTracked()).isTrue();
      assertThat(result.defaultOwnChequePercent()).isEqualTo(50);
      assertThat(result.content().validationIssues()).containsExactly("GBP:CHEQUE_SPLIT_REQUIRED");
    }
  }
}
