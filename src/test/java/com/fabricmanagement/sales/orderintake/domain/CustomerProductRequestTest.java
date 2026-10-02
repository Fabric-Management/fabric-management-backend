package com.fabricmanagement.sales.orderintake.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SOI D7: R16, N01, R18. */
class CustomerProductRequestTest {

  private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

  @Test
  @DisplayName("S01/N01: a request starts without product and quantity; unknown is not zero")
  void startsWithoutProductOrQuantity() {
    CustomerProductRequest request =
        record(details("Navy twill like the sample", null, null), false);
    assertThat(request.getStatus()).isEqualTo(CustomerRequestStatus.OPEN);
    assertThat(request.getRequestedQty()).isNull();
    assertThat(request.hasQuantity()).isFalse();
  }

  @Test
  @DisplayName("R16: at least a sample record, a file or a description")
  void needsSomethingToWorkOn() {
    assertThatThrownBy(() -> record(details(null, null, null), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(record(details(null, null, null), true).getStatus())
        .isEqualTo(CustomerRequestStatus.OPEN);
  }

  @Test
  @DisplayName("Quantity and unit come together; a known quantity is positive")
  void quantityAndUnitTogether() {
    assertThatThrownBy(() -> record(details("x", new BigDecimal("300"), null), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> record(details("x", BigDecimal.ZERO, "M"), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(record(details("x", new BigDecimal("300"), "m"), false).getUnit()).isEqualTo("M");
  }

  @Test
  @DisplayName("S11: only an approved request with quantity becomes a line")
  void resolveNeedsApprovalAndQuantity() {
    CustomerProductRequest request = record(details("x", null, null), false);
    request.nextRevision();
    request.sent();
    assertThatThrownBy(() -> request.resolve(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
    request.decided(CustomerRequestDecisionOutcome.APPROVED, request.requestTerms());
    assertThatThrownBy(() -> request.resolve(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
    request.update(details("x", new BigDecimal("300"), "M"), false);
    UUID line = UUID.randomUUID();
    request.resolve(line);
    assertThat(request.getStatus()).isEqualTo(CustomerRequestStatus.RESOLVED);
    assertThat(request.coversLine("t".repeat(64))).isFalse();
    request.decided(CustomerRequestDecisionOutcome.APPROVED, "t".repeat(64));
    assertThat(request.coversLine("t".repeat(64))).isTrue();
    assertThat(request.coversLine("u".repeat(64))).isFalse();
  }

  @Test
  @DisplayName("S21/A11: quantity or width changes alter the terms an approval covers")
  void requestTermsFollowCoveredFields() {
    CustomerProductRequest request = record(details("x", new BigDecimal("300"), "M"), false);
    String before = request.requestTerms();
    request.update(details("x (clarified)", new BigDecimal("300"), "M"), false);
    assertThat(request.requestTerms()).isEqualTo(before);
    request.update(details("x", new BigDecimal("320"), "M"), false);
    assertThat(request.requestTerms()).isNotEqualTo(before);
  }

  @Test
  @DisplayName("A10: taking a request off the order keeps its origin for delivery")
  void detachKeepsOrigin() {
    UUID order = UUID.randomUUID();
    CustomerProductRequest request =
        CustomerProductRequest.record(
            UUID.randomUUID(), order, details("x", null, null), false, UUID.randomUUID(), NOW);
    request.detach();
    assertThat(request.getSalesOrderId()).isNull();
    assertThat(request.getOriginOrderId()).isEqualTo(order);
    UUID next = UUID.randomUUID();
    request.attachTo(next);
    assertThat(request.getSalesOrderId()).isEqualTo(next);
    assertThat(request.getOriginOrderId()).isEqualTo(order);
  }

  @Test
  @DisplayName("NEEDS_INFO returns to OPEN when the request is completed")
  void needsInfoReopens() {
    CustomerProductRequest request = record(details("x", null, null), false);
    request.evaluated(CustomerRequestEvaluationOutcome.NEEDS_INFO);
    assertThat(request.getStatus()).isEqualTo(CustomerRequestStatus.NEEDS_INFO);
    request.update(details("x with the missing yarn count", null, null), false);
    assertThat(request.getStatus()).isEqualTo(CustomerRequestStatus.OPEN);
  }

  private static CustomerProductRequest record(
      CustomerProductRequest.Details details, boolean file) {
    return CustomerProductRequest.record(
        UUID.randomUUID(), UUID.randomUUID(), details, file, UUID.randomUUID(), NOW);
  }

  private static CustomerProductRequest.Details details(
      String description, BigDecimal quantity, String unit) {
    return new CustomerProductRequest.Details(
        description, null, quantity, unit, null, null, null, null, null, null);
  }
}
