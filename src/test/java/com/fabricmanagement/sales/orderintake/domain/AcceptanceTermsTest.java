package com.fabricmanagement.sales.orderintake.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SOI S21 / A11: only changes the acceptance covers ask for a new acceptance. */
class AcceptanceTermsTest {

  @Test
  @DisplayName("S21: an internal note change keeps the acceptance")
  void noteDoesNotChangeTerms() {
    SalesOrderLine line = line();
    String before = AcceptanceTerms.fingerprint(line);
    line.setProductDesc("changed internal note");
    assertThat(AcceptanceTerms.fingerprint(line)).isEqualTo(before);
  }

  @Test
  @DisplayName("S21: width, quantity, colour, unit and price changes need a new acceptance")
  void coveredTermsChangeTheFingerprint() {
    String base = AcceptanceTerms.fingerprint(line());

    SalesOrderLine width = line();
    width.setFinishedWidth(new BigDecimal("155.00"));
    SalesOrderLine quantity = line();
    quantity.setRequestedQty(new BigDecimal("600"));
    SalesOrderLine colour = line();
    colour.setColorId(UUID.randomUUID());
    SalesOrderLine price = line();
    price.updateUnitPrice(Money.of(new BigDecimal("4.20"), "EUR"));
    SalesOrderLine singleLot = line();
    singleLot.setSingleLotRequired(true);

    assertThat(AcceptanceTerms.fingerprint(width)).isNotEqualTo(base);
    assertThat(AcceptanceTerms.fingerprint(quantity)).isNotEqualTo(base);
    assertThat(AcceptanceTerms.fingerprint(colour)).isNotEqualTo(base);
    assertThat(AcceptanceTerms.fingerprint(price)).isNotEqualTo(base);
    assertThat(AcceptanceTerms.fingerprint(singleLot)).isNotEqualTo(base);
  }

  @Test
  @DisplayName("A change in the fourth decimal of the agreed price needs a new acceptance")
  void fourDecimalPriceChangeChangesTheFingerprint() {
    SalesOrderLine agreed = line();
    agreed.updatePricing("EUR", new BigDecimal("1.2345"), null, null);
    SalesOrderLine changed = line();
    changed.updatePricing("EUR", new BigDecimal("1.2349"), null, null);

    assertThat(AcceptanceTerms.fingerprint(changed))
        .isNotEqualTo(AcceptanceTerms.fingerprint(agreed));
  }

  @Test
  @DisplayName("Numeric scale and unit case do not count as a change")
  void representationIsNormalised() {
    SalesOrderLine plain = line();
    SalesOrderLine scaled = line();
    scaled.setRequestedQty(new BigDecimal("514.000"));
    scaled.setUnit("m");
    assertThat(AcceptanceTerms.fingerprint(scaled)).isEqualTo(AcceptanceTerms.fingerprint(plain));
  }

  private static final UUID PRODUCT = UUID.randomUUID();
  private static final UUID COLOR = UUID.randomUUID();

  private static SalesOrderLine line() {
    return SalesOrderLine.builder()
        .productId(PRODUCT)
        .colorId(COLOR)
        .finishedWidth(new BigDecimal("160.00"))
        .finishedWidthUnit("CM")
        .requestedQty(new BigDecimal("514"))
        .unit("M")
        .unitPrice(Money.of(new BigDecimal("4.10"), "EUR"))
        .build();
  }
}
