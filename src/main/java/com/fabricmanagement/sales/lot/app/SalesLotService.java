package com.fabricmanagement.sales.lot.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService.LotColourReference;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService.LotQualityReference;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService.ProductionSalesLotReference;
import com.fabricmanagement.production.execution.batch.api.query.ProductionSalesLotQueryService.ProductionSalesPieceReference;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.lot.dto.SalesLotColourDto;
import com.fabricmanagement.sales.lot.dto.SalesLotDto;
import com.fabricmanagement.sales.lot.dto.SalesLotPieceDto;
import com.fabricmanagement.sales.lot.dto.SalesLotQualityDto;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotPieceSnapshot;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSelectionRequest;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSnapshot;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSnapshotCodec;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesLotService {

  private final ProductionSalesLotQueryService productionLotQueryService;

  public List<SalesLotDto> listSalesLots() {
    return productionLotQueryService.listSaleableLots().stream().map(this::toDto).toList();
  }

  public List<QuoteLineLotSnapshot> resolveNewSelectionSnapshots(
      List<QuoteLineLotSelectionRequest> selections) {
    return resolveSelectionSnapshots(selections, List.of());
  }

  public List<QuoteLineLotSnapshot> resolveUpdateSelectionSnapshots(
      List<QuoteLineLotSelectionRequest> selections, String existingSnapshotJson) {
    return resolveSelectionSnapshots(
        selections, QuoteLineLotSnapshotCodec.fromJson(existingSnapshotJson));
  }

  private List<QuoteLineLotSnapshot> resolveSelectionSnapshots(
      List<QuoteLineLotSelectionRequest> selections, List<QuoteLineLotSnapshot> existingSnapshots) {
    if (selections == null || selections.isEmpty()) {
      return List.of();
    }
    Map<UUID, QuoteLineLotSnapshot> existingLots =
        existingSnapshots.stream()
            .collect(
                Collectors.toMap(QuoteLineLotSnapshot::lotId, Function.identity(), (a, b) -> a));
    Map<UUID, QuoteLineLotPieceSnapshot> existingPieces =
        existingSnapshots.stream()
            .flatMap(lot -> lot.pieces().stream())
            .collect(
                Collectors.toMap(
                    QuoteLineLotPieceSnapshot::stockUnitId, Function.identity(), (a, b) -> a));

    List<UUID> lotIds =
        selections.stream()
            .map(QuoteLineLotSelectionRequest::lotId)
            .filter(Objects::nonNull)
            .toList();
    Map<UUID, ProductionSalesLotReference> lots =
        productionLotQueryService.findLotsByIds(lotIds).stream()
            .collect(Collectors.toMap(ProductionSalesLotReference::id, Function.identity()));

    Map<UUID, QuoteLineLotSelectionRequest> uniqueSelections = new LinkedHashMap<>();
    selections.stream()
        .filter(selection -> selection.lotId() != null)
        .forEach(selection -> uniqueSelections.put(selection.lotId(), selection));

    return uniqueSelections.values().stream()
        .map(
            selection ->
                snapshotLot(selection, lots.get(selection.lotId()), existingLots, existingPieces))
        .toList();
  }

  private QuoteLineLotSnapshot snapshotLot(
      QuoteLineLotSelectionRequest selection,
      ProductionSalesLotReference lot,
      Map<UUID, QuoteLineLotSnapshot> existingLots,
      Map<UUID, QuoteLineLotPieceSnapshot> existingPieces) {
    QuoteLineLotSnapshot existingLot = existingLots.get(selection.lotId());
    if (lot == null) {
      if (existingLot != null) {
        return existingLot;
      }
      throw new NotFoundException("Lot not found: " + selection.lotId());
    }
    if (!lot.saleable() && existingLot == null) {
      throw SalesDomainException.referenceNoLongerAvailable("lot", lot.id().toString());
    }
    Set<UUID> selectedPieceIds =
        selection.stockUnitIds() == null
            ? Set.of()
            : selection.stockUnitIds().stream().collect(Collectors.toSet());
    Map<UUID, ProductionSalesPieceReference> piecesById =
        lot.pieces().stream()
            .collect(Collectors.toMap(ProductionSalesPieceReference::id, Function.identity()));
    List<QuoteLineLotPieceSnapshot> pieces =
        selectedPieceIds.stream()
            .map(pieceId -> snapshotPiece(pieceId, piecesById.get(pieceId), existingPieces))
            .toList();
    BigDecimal derivedQuantity =
        pieces.isEmpty()
            ? lot.availableQuantity()
            : pieces.stream()
                .map(QuoteLineLotPieceSnapshot::measure)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new QuoteLineLotSnapshot(
        lot.id(),
        lot.lotNo(),
        toColourDto(lot.colour()),
        toQualityDto(lot.quality()),
        lot.primaryMeasure(),
        lot.unit(),
        pieces,
        derivedQuantity);
  }

  private QuoteLineLotPieceSnapshot snapshotPiece(
      UUID pieceId,
      ProductionSalesPieceReference piece,
      Map<UUID, QuoteLineLotPieceSnapshot> existingPieces) {
    QuoteLineLotPieceSnapshot existing = existingPieces.get(pieceId);
    if (piece == null) {
      if (existing != null) {
        return existing;
      }
      throw new NotFoundException("Stock unit not found: " + pieceId);
    }
    if (!piece.selectable() && existing == null) {
      throw SalesDomainException.referenceNoLongerAvailable("stockUnit", piece.id().toString());
    }
    return new QuoteLineLotPieceSnapshot(
        piece.id(), piece.pieceNo(), piece.primaryMeasureValue(), piece.primaryMeasureUnit());
  }

  private SalesLotDto toDto(ProductionSalesLotReference ref) {
    return new SalesLotDto(
        ref.id(),
        ref.lotNo(),
        ref.status(),
        ref.primaryMeasure(),
        ref.unit(),
        toQualityDto(ref.quality()),
        toColourDto(ref.colour()),
        ref.availableQuantity(),
        ref.pieces().stream().map(this::toPieceDto).toList());
  }

  private SalesLotPieceDto toPieceDto(ProductionSalesPieceReference piece) {
    return new SalesLotPieceDto(
        piece.id(),
        piece.pieceNo(),
        piece.packageType(),
        piece.length(),
        piece.lengthUnit(),
        piece.weight(),
        piece.weightUnit(),
        piece.status(),
        piece.softReservedCount());
  }

  private SalesLotQualityDto toQualityDto(LotQualityReference quality) {
    return quality == null
        ? null
        : new SalesLotQualityDto(quality.id(), quality.code(), quality.name());
  }

  private SalesLotColourDto toColourDto(LotColourReference colour) {
    return colour == null
        ? null
        : new SalesLotColourDto(
            colour.id(), colour.code(), colour.name(), colour.colorHex(), colour.colourLabel());
  }
}
