package com.fabricmanagement.sales.lot.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService.LotColourReference;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService.LotQualityReference;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService.ProductionSalesLotIntentReference;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService.ProductionSalesLotReference;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService.ProductionSalesPieceReference;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.lot.dto.SalesLotDto;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotPieceSnapshot;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSelectionRequest;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSnapshot;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSnapshotCodec;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesLotServiceTest {

  @Mock private ProductionSalesLotQueryService productionLotQueryService;

  @InjectMocks private SalesLotService service;

  private final UUID lotId = UUID.randomUUID();
  private final UUID stockUnitId = UUID.randomUUID();

  @Test
  void shouldMapSalesLotProjectionWithPiecesAndAdvisoryQuantities() {
    UUID quoteLineId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    ProductionSalesLotReference lot =
        lot(
            true,
            List.of(piece(stockUnitId, true, new BigDecimal("120.000"), 2L)),
            List.of(
                new ProductionSalesLotIntentReference(
                    quoteId, "Q-2026-001", "Ada Marketer", new BigDecimal("20.000"), today())));
    when(productionLotQueryService.listSaleableLots(quoteLineId)).thenReturn(List.of(lot));

    List<SalesLotDto> lots = service.listSalesLots(quoteLineId);

    assertEquals(1, lots.size());
    SalesLotDto dto = lots.getFirst();
    assertEquals(lotId, dto.id());
    assertEquals("LOT-001", dto.lotNo());
    assertEquals("LENGTH", dto.primaryMeasure());
    assertEquals("M", dto.unit());
    assertEquals(new BigDecimal("100.000"), dto.freeQuantity());
    assertEquals("NAVY-01", dto.colour().colourCode());
    assertEquals("A", dto.quality().code());
    assertEquals(1, dto.intents().size());
    assertEquals("Ada Marketer", dto.intents().getFirst().marketerName());
    assertEquals(2L, dto.pieces().getFirst().softReservedCount());
  }

  @Test
  void shouldSnapshotSelectedPiecesAndDeriveQuantityFromPrimaryMeasure() {
    ProductionSalesLotReference lot =
        lot(true, List.of(piece(stockUnitId, true, new BigDecimal("120.000"), 0L)), List.of());
    when(productionLotQueryService.findLotsByIds(List.of(lotId))).thenReturn(List.of(lot));

    List<QuoteLineLotSnapshot> snapshots =
        service.resolveNewSelectionSnapshots(
            List.of(new QuoteLineLotSelectionRequest(lotId, List.of(stockUnitId))));

    assertEquals(1, snapshots.size());
    QuoteLineLotSnapshot snapshot = snapshots.getFirst();
    assertEquals(lotId, snapshot.lotId());
    assertEquals(new BigDecimal("120.000"), snapshot.derivedQuantity());
    assertEquals(stockUnitId, snapshot.pieces().getFirst().stockUnitId());
    assertEquals(new BigDecimal("120.000"), snapshot.pieces().getFirst().measure());
  }

  @Test
  void shouldReturn404ForUnknownLotSelection() {
    when(productionLotQueryService.findLotsByIds(List.of(lotId))).thenReturn(List.of());

    assertThrows(
        NotFoundException.class,
        () ->
            service.resolveNewSelectionSnapshots(
                List.of(new QuoteLineLotSelectionRequest(lotId, List.of(stockUnitId)))));
  }

  @Test
  void shouldRejectNewUnavailableLotSelectionWithSharedErrorCode() {
    ProductionSalesLotReference lot = lot(false, List.of(), List.of());
    when(productionLotQueryService.findLotsByIds(List.of(lotId))).thenReturn(List.of(lot));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class,
            () ->
                service.resolveNewSelectionSnapshots(
                    List.of(new QuoteLineLotSelectionRequest(lotId, List.of()))));

    assertEquals("SALES_015_REFERENCE_NO_LONGER_AVAILABLE", ex.getErrorCode());
    assertEquals(409, ex.getHttpStatus());
  }

  @Test
  void shouldRejectNewUnavailablePieceSelectionWithSharedErrorCode() {
    ProductionSalesLotReference lot =
        lot(true, List.of(piece(stockUnitId, false, new BigDecimal("120.000"), 0L)), List.of());
    when(productionLotQueryService.findLotsByIds(List.of(lotId))).thenReturn(List.of(lot));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class,
            () ->
                service.resolveNewSelectionSnapshots(
                    List.of(new QuoteLineLotSelectionRequest(lotId, List.of(stockUnitId)))));

    assertEquals("SALES_015_REFERENCE_NO_LONGER_AVAILABLE", ex.getErrorCode());
    assertEquals(409, ex.getHttpStatus());
  }

  @Test
  void shouldAcceptExistingUnavailablePieceEchoOnFullStateUpdate() {
    QuoteLineLotSnapshot existing =
        new QuoteLineLotSnapshot(
            lotId,
            "LOT-001",
            null,
            null,
            "LENGTH",
            "M",
            List.of(
                new QuoteLineLotPieceSnapshot(
                    stockUnitId, "ROLL-001", new BigDecimal("120.000"), "M")),
            new BigDecimal("120.000"));
    ProductionSalesLotReference lot = lot(true, List.of(), List.of());
    when(productionLotQueryService.findLotsByIds(List.of(lotId))).thenReturn(List.of(lot));

    List<QuoteLineLotSnapshot> snapshots =
        service.resolveUpdateSelectionSnapshots(
            List.of(new QuoteLineLotSelectionRequest(lotId, List.of(stockUnitId))),
            QuoteLineLotSnapshotCodec.toJson(List.of(existing)));

    assertEquals(1, snapshots.size());
    QuoteLineLotSnapshot snapshot = snapshots.getFirst();
    assertEquals(new BigDecimal("120.000"), snapshot.derivedQuantity());
    assertEquals(stockUnitId, snapshot.pieces().getFirst().stockUnitId());
    assertEquals("ROLL-001", snapshot.pieces().getFirst().pieceNo());
  }

  private ProductionSalesLotReference lot(
      boolean saleable,
      List<ProductionSalesPieceReference> pieces,
      List<ProductionSalesLotIntentReference> intents) {
    return new ProductionSalesLotReference(
        lotId,
        "LOT-001",
        saleable ? "AVAILABLE" : "DEPLETED",
        saleable,
        "LENGTH",
        "M",
        new LotQualityReference(UUID.randomUUID(), "A", "Grade A", true, true),
        new LotColourReference(UUID.randomUUID(), "NAVY-01", "Navy", "#001F3F", null),
        new BigDecimal("120.000"),
        new BigDecimal("120.000"),
        new BigDecimal("10.000"),
        new BigDecimal("10.000"),
        new BigDecimal("100.000"),
        intents,
        pieces);
  }

  private ProductionSalesPieceReference piece(
      UUID id, boolean selectable, BigDecimal length, long softReservedCount) {
    return new ProductionSalesPieceReference(
        id,
        "ROLL-001",
        "ROLL",
        length,
        "M",
        new BigDecimal("45.000"),
        "KG",
        length,
        "M",
        selectable ? "AVAILABLE" : "DEPLETED",
        selectable,
        softReservedCount,
        UUID.randomUUID());
  }

  private LocalDate today() {
    return LocalDate.of(2026, 7, 8);
  }
}
