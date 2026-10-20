package com.fabricmanagement.production.core.stockunit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSoftHold;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSoftHoldStatus;
import com.fabricmanagement.production.core.stockunit.domain.event.StockUnitSoftHoldPlacedEvent;
import com.fabricmanagement.production.core.stockunit.domain.event.StockUnitSoftHoldReleasedEvent;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitSoftHoldRepository;
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

  @Mock
  private com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository
      pieces;

  @Mock private com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort ownership;

  @Mock
  private com.fabricmanagement.production.core.batch.infra.repository
          .BatchLotQuantityIntentRepository
      intents;

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
  void quoteConversionMovesTheSameHoldAndIntentRowsWithoutAnAvailabilityGap() {
    UUID quote = UUID.randomUUID(),
        order = UUID.randomUUID(),
        orderLine = UUID.randomUUID(),
        batch = UUID.randomUUID();
    var hold = StockUnitSoftHold.place(tenantId, quoteLineId, keptStockUnitId);
    var intent =
        com.fabricmanagement.production.core.batch.domain.BatchLotQuantityIntent.place(
            tenantId,
            quote,
            "QU-2026-001",
            quoteLineId,
            UUID.randomUUID(),
            "Oliver Reed",
            batch,
            new java.math.BigDecimal("100"),
            "M",
            java.time.LocalDate.of(2026, 10, 30));
    var unit =
        com.fabricmanagement.production.core.stockunit.domain.StockUnit.builder()
            .batchId(batch)
            .build();
    unit.setId(keptStockUnitId);
    hold.setId(UUID.randomUUID());
    intent.setId(UUID.randomUUID());
    UUID originalHoldId = hold.getId(), originalIntentId = intent.getId();
    when(repository.findByTenantIdAndOwnerLineId(tenantId, quoteLineId)).thenReturn(List.of(hold));
    when(intents.findByTenantIdAndOwnerLineId(tenantId, quoteLineId)).thenReturn(List.of(intent));
    when(pieces.findByTenantIdAndIdInAndIsActiveTrue(tenantId, List.of(keptStockUnitId)))
        .thenReturn(List.of(unit));

    service.transferQuoteHolds(quote, quoteLineId, order, "SO-2026-001", orderLine);

    assertEquals(originalHoldId, hold.getId());
    assertEquals(originalIntentId, intent.getId());
    assertEquals(StockUnitSoftHoldStatus.ACTIVE, hold.getStatus());
    assertEquals("SALES_ORDER", hold.getOwnerType());
    assertEquals(orderLine, hold.getOwnerLineId());
    assertEquals(order, intent.getOwnerId());
    assertEquals(orderLine, intent.getOwnerLineId());
    assertEquals(null, intent.getQuoteId());
    assertEquals(null, hold.getQuoteLineId());
    assertEquals(null, intent.getExpiresAt());
    verify(ownership).assertAvailable(List.of(keptStockUnitId), quoteLineId, null);
    org.mockito.Mockito.verifyNoInteractions(eventPublisher);
  }

  @Test
  void quoteConversionRefusesAPieceWithoutAnIntentOnItsOwnLot() {
    UUID quote = UUID.randomUUID();
    var hold = StockUnitSoftHold.place(tenantId, quoteLineId, keptStockUnitId);
    var intent =
        com.fabricmanagement.production.core.batch.domain.BatchLotQuantityIntent.place(
            tenantId,
            quote,
            "QU-2026-001",
            quoteLineId,
            UUID.randomUUID(),
            "Oliver Reed",
            UUID.randomUUID(),
            new java.math.BigDecimal("100"),
            "M",
            java.time.LocalDate.of(2026, 10, 30));
    var unit =
        com.fabricmanagement.production.core.stockunit.domain.StockUnit.builder()
            .batchId(UUID.randomUUID())
            .build();
    unit.setId(keptStockUnitId);
    when(repository.findByTenantIdAndOwnerLineId(tenantId, quoteLineId)).thenReturn(List.of(hold));
    when(intents.findByTenantIdAndOwnerLineId(tenantId, quoteLineId)).thenReturn(List.of(intent));
    when(pieces.findByTenantIdAndIdInAndIsActiveTrue(tenantId, List.of(keptStockUnitId)))
        .thenReturn(List.of(unit));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                service.transferQuoteHolds(
                    quote, quoteLineId, UUID.randomUUID(), "SO-2026-001", UUID.randomUUID()))
        .isInstanceOf(
            com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitHeldException
                .class);
    assertEquals("QUOTE", hold.getOwnerType());
    assertEquals("QUOTE", intent.getOwnerType());
  }

  @Test
  void shouldReleaseAllActiveHoldsForLine() {
    StockUnitSoftHold hold = StockUnitSoftHold.place(tenantId, quoteLineId, keptStockUnitId);
    when(repository.findByTenantIdAndOwnerLineId(tenantId, quoteLineId)).thenReturn(List.of(hold));
    when(repository.save(any(StockUnitSoftHold.class))).thenAnswer(inv -> inv.getArgument(0));

    service.releaseHolds(quoteLineId);

    assertEquals(StockUnitSoftHoldStatus.RELEASED, hold.getStatus());
    verify(eventPublisher).publishEvent(any(StockUnitSoftHoldReleasedEvent.class));
  }
}
