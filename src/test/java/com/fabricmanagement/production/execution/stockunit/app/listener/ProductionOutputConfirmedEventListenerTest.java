package com.fabricmanagement.production.execution.stockunit.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.output.domain.event.ProductionOutputConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductionOutputConfirmedEventListenerTest {

  @Mock private StockUnitService stockUnitService;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private IdempotentEventHandler idempotentEventHandler;
  @InjectMocks private ProductionOutputConfirmedEventListener listener;

  @Test
  void confirmedOutputUsesTrustedProductionBulkCreationPath() {
    executeHandlerBody();
    UUID batchId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    ProductionOutputConfirmedEvent event = event(batchId, itemId);

    listener.onProductionOutputConfirmed(event);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockUnitService.CreateStockUnitRequest>> requestsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(stockUnitService)
        .createBulk(eq(batchId), requestsCaptor.capture(), eq(TenantContext.SYSTEM_ACTOR_ID));
    assertThat(requestsCaptor.getValue())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.sourceType()).isEqualTo(StockUnitSourceType.PRODUCTION);
              assertThat(request.sourceId()).isEqualTo(itemId);
            });
  }

  @Test
  void creationFailureEscapesSoIdempotentDeliveryCanRetry() {
    executeHandlerBody();
    ProductionOutputConfirmedEvent event = event(UUID.randomUUID(), UUID.randomUUID());
    doThrow(new IllegalStateException("creation failed"))
        .when(stockUnitService)
        .createBulk(any(), any(), any());

    assertThatThrownBy(() -> listener.onProductionOutputConfirmed(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("creation failed");
  }

  private ProductionOutputConfirmedEvent event(UUID batchId, UUID itemId) {
    return new ProductionOutputConfirmedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        batchId,
        UUID.randomUUID(),
        ProductType.FABRIC,
        "KG",
        UUID.randomUUID(),
        List.of(
            new ProductionOutputConfirmedEvent.OutputItemData(
                itemId,
                "OUTPUT-ROLL-1",
                PackageType.ROLL,
                new BigDecimal("25"),
                null,
                UUID.randomUUID())));
  }

  private void executeHandlerBody() {
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(3).run();
              return null;
            })
        .when(idempotentEventHandler)
        .executeOnce(any(), any(), any(), any());
  }
}
