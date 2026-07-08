package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.lot.dto.SalesLotColourDto;
import com.fabricmanagement.sales.lot.dto.SalesLotQualityDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuoteLineLotSnapshot(
    UUID lotId,
    String lotNo,
    SalesLotColourDto colour,
    SalesLotQualityDto quality,
    String primaryMeasure,
    String unit,
    List<QuoteLineLotPieceSnapshot> pieces,
    BigDecimal derivedQuantity) {

  public QuoteLineLotSnapshot {
    pieces = pieces == null ? List.of() : List.copyOf(pieces);
  }
}
