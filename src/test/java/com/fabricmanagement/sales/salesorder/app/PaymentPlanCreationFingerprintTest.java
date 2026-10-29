package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanInput;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentPlanCreationFingerprintTest {
  @Test
  void createReplayFingerprintIncludesDatesAndManualAmounts() {
    var replay =
        new OrderCreationReplay(
            mock(SalesOrderRepository.class),
            mock(javax.sql.DataSource.class),
            new ObjectMapper().findAndRegisterModules());
    var request = new CreateSalesOrderRequest();
    request.setPaymentPlan(input("2026-10-30", "100.00"));
    String first = replay.fingerprint(request);
    request.setPaymentPlan(input("2026-10-30", "100.00"));
    assertThat(replay.fingerprint(request)).isEqualTo(first);
    request.setPaymentPlan(input("2026-10-15", "100.00"));
    assertThat(replay.fingerprint(request)).isNotEqualTo(first);
    request.setPaymentPlan(input("2026-10-30", "90.00"));
    assertThat(replay.fingerprint(request)).isNotEqualTo(first);
  }

  @Test
  void absentNewFieldsDoNotChangeLegacyCreateReplayContent() throws Exception {
    var mapper = new ObjectMapper().findAndRegisterModules();
    var legacy = new CreateSalesOrderRequest();
    String json = mapper.writeValueAsString(legacy);
    assertThat(json).doesNotContain("paymentPlan", "paymentNote");
  }

  private static PaymentPlanInput input(String date, String amount) {
    return new PaymentPlanInput(
        List.of(
            new PaymentPlanInput.CurrencyInput(
                "GBP",
                null,
                List.of(
                    new PaymentPlanInput.Row(
                        null,
                        Kind.INSTALMENT,
                        new BigDecimal(amount),
                        Method.CASH,
                        DateTrigger.EXPLICIT_DATE,
                        LocalDate.parse(date),
                        null)))));
  }
}
