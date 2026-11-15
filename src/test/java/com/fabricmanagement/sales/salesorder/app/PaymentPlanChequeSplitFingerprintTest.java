package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanInput;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Save;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PAYMENT-PLAN-7 R5: requests and contents without a split hash exactly as before the slice. */
class PaymentPlanChequeSplitFingerprintTest {

  /** SHA-256 of the canonical JSON this request had before the split existed. */
  private static final String PRE_SLICE_HASH =
      "096ad28adef85f7bfad088e1e42782e110394c8b26768eda4679d1fd3f976080";

  private static Save save(Integer own) {
    return new Save(
        UUID.fromString("6f1c2b7e-3a44-4d55-9e1f-0a2b3c4d5e6f"),
        3L,
        1L,
        new PaymentPlanInput(List.of(new PaymentPlanInput.CurrencyInput("GBP", null, null, own))),
        "Customer asked for a new date");
  }

  @Test
  void aRequestWithoutTheSplitKeepsItsStoredHash() {
    assertThat(CanonicalJsonFingerprint.of(save(null))).isEqualTo(PRE_SLICE_HASH);
  }

  @Test
  void aRequestWithTheSplitHashesDifferently() {
    assertThat(CanonicalJsonFingerprint.of(save(60))).isNotEqualTo(PRE_SLICE_HASH);
    assertThat(CanonicalJsonFingerprint.of(save(60)))
        .isNotEqualTo(CanonicalJsonFingerprint.of(save(40)));
  }

  @Test
  void contentWithoutASplitSerialisesWithoutTheField() throws Exception {
    var mapper = new ObjectMapper().findAndRegisterModules();
    var none =
        new PaymentPlanContent.CurrencyGroup("GBP", new BigDecimal("10.00"), null, List.of());
    var agreed =
        new PaymentPlanContent.CurrencyGroup("GBP", new BigDecimal("10.00"), null, List.of(), 60);
    assertThat(mapper.writeValueAsString(none)).doesNotContain("ownChequePercent");
    assertThat(mapper.writeValueAsString(agreed)).contains("\"ownChequePercent\":60");
  }
}
