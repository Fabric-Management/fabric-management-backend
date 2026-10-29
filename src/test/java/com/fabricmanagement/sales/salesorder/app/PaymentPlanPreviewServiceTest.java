package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanInput;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Frequency;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Preview;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentPlanPreviewServiceTest {
  @Test
  void previewUsesBackendGrandTotalIncludingDiscountAndTax() {
    try (var validation = Validation.buildDefaultValidatorFactory()) {
      var service = new PaymentPlanPreviewService(validation.getValidator());
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
}
