package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.app.ConfirmationGate;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderLineRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderIntakeHooksTest {

  private final OrderIntakeHooks hooks =
      new OrderIntakeHooks(mock(CatalogLineValidator.class), mock(ConfirmationGate.class));

  @Test
  @DisplayName("R04/R19: the full replace cannot swap an existing line's product")
  void productChangeNeedsTheCorrectionCommand() {
    SalesOrderLine existing =
        SalesOrderLine.builder()
            .productId(UUID.randomUUID())
            .requestedQty(BigDecimal.TEN)
            .unit("M")
            .build();
    existing.setId(UUID.randomUUID());

    UpdateSalesOrderLineRequest same = new UpdateSalesOrderLineRequest();
    same.setId(existing.getId());
    same.setProductId(existing.getProductId());
    assertThatCode(() -> hooks.assertProductsUnchanged(List.of(existing), List.of(same)))
        .doesNotThrowAnyException();

    UpdateSalesOrderLineRequest added = new UpdateSalesOrderLineRequest();
    added.setProductId(UUID.randomUUID());
    assertThatCode(() -> hooks.assertProductsUnchanged(List.of(existing), List.of(same, added)))
        .doesNotThrowAnyException();

    UpdateSalesOrderLineRequest swapped = new UpdateSalesOrderLineRequest();
    swapped.setId(existing.getId());
    swapped.setProductId(UUID.randomUUID());
    assertThatThrownBy(() -> hooks.assertProductsUnchanged(List.of(existing), List.of(swapped)))
        .isInstanceOf(OrderIntakeException.class)
        .hasMessageContaining("product correction");
  }
}
