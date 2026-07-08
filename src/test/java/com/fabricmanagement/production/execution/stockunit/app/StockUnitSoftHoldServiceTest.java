package com.fabricmanagement.production.execution.stockunit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSoftHold;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSoftHoldStatus;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitSoftHoldPlacedEvent;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitSoftHoldReleasedEvent;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitSoftHoldRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class StockUnitSoftHoldServiceTest {

  @Mock private StockUnitSoftHoldRepository repository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private StockUnitSoftHoldService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID quoteLineId = UUID.randomUUID();
  private final UUID keptStockUnitId = UUID.randomUUID();
  private final UUID releasedStockUnitId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldReplaceActiveHoldsAndReleaseRemovedPieces() {
    StockUnitSoftHold kept = StockUnitSoftHold.place(tenantId, quoteLineId, keptStockUnitId);
    StockUnitSoftHold released =
        StockUnitSoftHold.place(tenantId, quoteLineId, releasedStockUnitId);
    UUID newStockUnitId = UUID.randomUUID();

    when(repository.findByTenantIdAndQuoteLineId(tenantId, quoteLineId))
        .thenReturn(List.of(kept, released));
    when(repository.findByTenantIdAndQuoteLineIdAndStockUnitId(
            tenantId, quoteLineId, keptStockUnitId))
        .thenReturn(Optional.of(kept));
    when(repository.findByTenantIdAndQuoteLineIdAndStockUnitId(
            tenantId, quoteLineId, newStockUnitId))
        .thenReturn(Optional.empty());
    when(repository.save(any(StockUnitSoftHold.class))).thenAnswer(inv -> inv.getArgument(0));

    service.replaceHolds(quoteLineId, List.of(keptStockUnitId, newStockUnitId));

    assertEquals(StockUnitSoftHoldStatus.RELEASED, released.getStatus());
    verify(repository, times(2)).save(any(StockUnitSoftHold.class));
    verify(eventPublisher).publishEvent(any(StockUnitSoftHoldReleasedEvent.class));
    verify(eventPublisher).publishEvent(any(StockUnitSoftHoldPlacedEvent.class));
  }

  @Test
  void shouldReleaseAllActiveHoldsForLine() {
    StockUnitSoftHold hold = StockUnitSoftHold.place(tenantId, quoteLineId, keptStockUnitId);
    when(repository.findByTenantIdAndQuoteLineId(tenantId, quoteLineId)).thenReturn(List.of(hold));
    when(repository.save(any(StockUnitSoftHold.class))).thenAnswer(inv -> inv.getArgument(0));

    service.releaseHolds(quoteLineId);

    assertEquals(StockUnitSoftHoldStatus.RELEASED, hold.getStatus());
    verify(eventPublisher).publishEvent(any(StockUnitSoftHoldReleasedEvent.class));
  }
}
