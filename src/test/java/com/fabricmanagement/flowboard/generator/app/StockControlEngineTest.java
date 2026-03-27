package com.fabricmanagement.flowboard.generator.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.flowboard.generator.domain.port.out.StockQueryPort;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockControlEngine")
class StockControlEngineTest {

  @Mock private StockQueryPort stockPort;

  @InjectMocks private StockControlEngine engine;

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Test
  @DisplayName("Herhangi bir sipariş için PRODUCTION task kararı döner (stub)")
  void analyze_returnsProductionDecision() {
    SalesOrderConfirmedEvent event =
        new SalesOrderConfirmedEvent(
            TENANT_ID,
            UUID.randomUUID(),
            "SO-001",
            UUID.randomUUID(),
            "Customer",
            BigDecimal.valueOf(100),
            "KG",
            LocalDate.now().plusDays(14));

    // Mock port
    // TenantContext is statically resolved, which might be null. For simplicity, we mock any().
    when(stockPort.getAvailableStockForOrder(any(), any())).thenReturn(BigDecimal.ZERO);

    List<StockControlEngine.StockDecision> decisions = engine.analyze(event);

    assertThat(decisions).hasSize(1);
    assertThat(decisions.get(0).taskType()).isEqualTo(TaskType.PRODUCTION);
    assertThat(decisions.get(0).quantity()).isEqualByComparingTo(BigDecimal.valueOf(100));
  }

  @Test
  @DisplayName("Sıfır miktar için de karar döner")
  void analyze_zeroQuantity_stillReturnsDecision() {
    SalesOrderConfirmedEvent event =
        new SalesOrderConfirmedEvent(
            TENANT_ID,
            UUID.randomUUID(),
            "SO-002",
            UUID.randomUUID(),
            "Customer",
            BigDecimal.ZERO,
            "KG",
            LocalDate.now().plusDays(7));

    when(stockPort.getAvailableStockForOrder(any(), any())).thenReturn(BigDecimal.ZERO);
    List<StockControlEngine.StockDecision> decisions = engine.analyze(event);

    assertThat(decisions).isNotEmpty();
  }
}
