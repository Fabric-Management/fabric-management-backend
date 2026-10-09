package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The opaque history cursor and the page limit (CEDIT-09 §3.2, H08). */
class SalesOrderFieldHistoryCursorTest {

  private final UUID tenant = UUID.randomUUID();
  private final UUID order = UUID.randomUUID();
  private final UUID last = UUID.randomUUID();

  @Test
  @DisplayName("A cursor reads back as written, for its own tenant and order")
  void roundTrip() {
    SalesOrderFieldHistoryCursor cursor =
        new SalesOrderFieldHistoryCursor(tenant, order, 12L, 9L, last);

    String text = cursor.encode();

    assertThat(text).matches("^[A-Za-z0-9_-]+$");
    assertThat(text.length()).isLessThanOrEqualTo(SalesOrderFieldHistoryDtos.MAX_CURSOR_LENGTH);
    assertThat(SalesOrderFieldHistoryCursor.decode(text, tenant, order)).isEqualTo(cursor);
  }

  @Test
  @DisplayName("H08: another order's or tenant's cursor is refused, naming cursor")
  void foreignContextIsRefused() {
    String text = new SalesOrderFieldHistoryCursor(tenant, order, 5L, 5L, last).encode();

    assertInvalid(() -> SalesOrderFieldHistoryCursor.decode(text, tenant, UUID.randomUUID()));
    assertInvalid(() -> SalesOrderFieldHistoryCursor.decode(text, UUID.randomUUID(), order));
  }

  @Test
  @DisplayName("H08: garbage, too long, another format, impossible bounds are refused")
  void malformedCursorsAreRefused() {
    String tooLong = "A".repeat(SalesOrderFieldHistoryDtos.MAX_CURSOR_LENGTH + 1);
    assertInvalid(() -> SalesOrderFieldHistoryCursor.decode(tooLong, tenant, order));
    assertInvalid(() -> SalesOrderFieldHistoryCursor.decode("", tenant, order));
    assertInvalid(() -> SalesOrderFieldHistoryCursor.decode("not a cursor!", tenant, order));
    assertInvalid(() -> SalesOrderFieldHistoryCursor.decode("A", tenant, order));
    assertInvalid(() -> decode("2|" + tenant + "|" + order + "|5|5|" + last));
    assertInvalid(() -> decode("1|" + tenant + "|" + order + "|5|6|" + last));
    assertInvalid(() -> decode("1|" + tenant + "|" + order + "|-1|0|" + last));
    assertInvalid(() -> decode("1|" + tenant + "|" + order + "|99999999999999999999|0|" + last));
    assertInvalid(() -> decode("1|" + tenant + "|" + order + "|5|5|" + last + "|x"));
    assertInvalid(
        () -> decode("1|" + tenant + "|" + order + "|5|5|" + last.toString().toUpperCase()));
    assertInvalid(() -> decode("1|" + tenant + "|" + order + "|5|5|1-1-1-1-1"));
  }

  @Test
  @DisplayName("H08: limit defaults to 30 and accepts 1 to 100 only, naming limit")
  void limits() {
    assertThat(SalesOrderFieldHistoryCursor.limit(null)).isEqualTo(30);
    assertThat(SalesOrderFieldHistoryCursor.limit(1)).isEqualTo(1);
    assertThat(SalesOrderFieldHistoryCursor.limit(100)).isEqualTo(100);
    DomainException zero = failure(() -> SalesOrderFieldHistoryCursor.limit(0));
    assertThat(zero.getHttpStatus()).isEqualTo(422);
    assertThat(zero.getErrorCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(zero.getFieldErrors()).containsOnlyKeys("limit");
    assertThat(failure(() -> SalesOrderFieldHistoryCursor.limit(101)).getFieldErrors())
        .containsOnlyKeys("limit");
  }

  @Test
  @DisplayName("A snapshot beyond the order is refused as a cursor error")
  void beyondOrder() {
    DomainException failure = SalesOrderFieldHistoryCursor.beyondOrder();
    assertThat(failure.getHttpStatus()).isEqualTo(422);
    assertThat(failure.getFieldErrors()).containsOnlyKeys("cursor");
  }

  private SalesOrderFieldHistoryCursor decode(String plain) {
    String text =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    return SalesOrderFieldHistoryCursor.decode(text, tenant, order);
  }

  private static DomainException failure(Runnable step) {
    Throwable thrown = catchThrowable(step::run);
    assertThat(thrown).as("a refused request").isInstanceOf(DomainException.class);
    return (DomainException) thrown;
  }

  private static void assertInvalid(Runnable step) {
    DomainException failure = failure(step);
    assertThat(failure.getHttpStatus()).isEqualTo(422);
    assertThat(failure.getErrorCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(failure.getFieldErrors()).containsOnlyKeys("cursor");
  }
}
