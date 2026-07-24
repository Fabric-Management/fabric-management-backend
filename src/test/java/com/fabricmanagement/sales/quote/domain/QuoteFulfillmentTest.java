package com.fabricmanagement.sales.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteFulfillmentTest {

  @Test
  void manualModeDerivesConfirmedManualAndNullClearsToPending() {
    QuoteLine line = new QuoteLine();

    assertThat(line.getFulfillmentMode()).isNull();
    assertThat(line.getFulfillmentDeterminationStatus())
        .isEqualTo(FulfillmentDeterminationStatus.PENDING);
    assertThat(line.getFulfillmentDeterminationMethod()).isNull();

    line.applyManualFulfillmentMode(FulfillmentMode.MAKE_TO_ORDER);

    assertThat(line.getFulfillmentMode()).isEqualTo(FulfillmentMode.MAKE_TO_ORDER);
    assertThat(line.getFulfillmentDeterminationStatus())
        .isEqualTo(FulfillmentDeterminationStatus.CONFIRMED);
    assertThat(line.getFulfillmentDeterminationMethod())
        .isEqualTo(FulfillmentDeterminationMethod.MANUAL);

    line.applyManualFulfillmentMode(null);

    assertThat(line.getFulfillmentMode()).isNull();
    assertThat(line.getFulfillmentDeterminationStatus())
        .isEqualTo(FulfillmentDeterminationStatus.PENDING);
    assertThat(line.getFulfillmentDeterminationMethod()).isNull();
  }

  @Test
  void mixedFulfillmentIsDerivedFromDistinctNonNullLineModes() {
    Quote quote = new Quote();
    QuoteLine pending = new QuoteLine();
    QuoteLine stock = new QuoteLine();
    stock.applyManualFulfillmentMode(FulfillmentMode.STOCK);
    QuoteLine production = new QuoteLine();
    production.applyManualFulfillmentMode(FulfillmentMode.MAKE_TO_ORDER);

    quote.addLine(pending);
    assertThat(quote.isMixedFulfillment()).isFalse();

    quote.addLine(stock);
    assertThat(quote.isMixedFulfillment()).isFalse();

    QuoteLine anotherStock = new QuoteLine();
    anotherStock.applyManualFulfillmentMode(FulfillmentMode.STOCK);
    quote.addLine(anotherStock);
    assertThat(quote.isMixedFulfillment()).isFalse();

    quote.addLine(production);
    assertThat(quote.isMixedFulfillment()).isTrue();
  }
}
