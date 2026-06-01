package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStatusTest {

  @Test
  void canEdit_whenDraft_returnsTrue() {
    assertThat(OrderStatus.DRAFT.canEdit()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"DRAFT"},
      mode = EnumSource.Mode.EXCLUDE)
  void canEdit_whenNotDraft_returnsFalse(OrderStatus status) {
    assertThat(status.canEdit()).isFalse();
  }
}
