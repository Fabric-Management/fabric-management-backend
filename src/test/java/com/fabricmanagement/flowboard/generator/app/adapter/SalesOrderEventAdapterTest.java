package com.fabricmanagement.flowboard.generator.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fabricmanagement.flowboard.generator.app.StockControlEngine;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class SalesOrderEventAdapterTest {
  @Test
  void governedConfirmationNeverInvokesStockControlOrProducesEarlyTasks() {
    StockControlEngine engine = mock(StockControlEngine.class);
    SalesOrderEventAdapter adapter = new SalesOrderEventAdapter(engine);
    var event =
        new SalesOrderConfirmedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "SO-1",
            UUID.randomUUID(),
            "Customer",
            BigDecimal.ONE,
            "kg",
            LocalDate.now(),
            List.of(),
            OrderCoverRegime.GOVERNED);
    assertThat(adapter.determineTaskTypes(event, List.of())).isEmpty();
    verifyNoInteractions(engine);
  }
}
