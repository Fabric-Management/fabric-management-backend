package com.fabricmanagement.sales.lot.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SalesLotDto(
    UUID id,
    String lotNo,
    String status,
    String primaryMeasure,
    String unit,
    SalesLotQualityDto quality,
    SalesLotColourDto colour,
    BigDecimal availableQuantity,
    List<SalesLotPieceDto> pieces) {}
