package com.fabricmanagement.sales.lot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(name = "SalesLotDto", description = "Sales-readable lot availability and selectable pieces")
public record SalesLotDto(
    UUID id,
    String lotNo,
    String status,
    String primaryMeasure,
    String unit,
    SalesLotQualityDto quality,
    SalesLotColourDto colour,
    BigDecimal availableQuantity,
    BigDecimal physicalQuantity,
    BigDecimal softIntentQuantity,
    BigDecimal hardReservedQuantity,
    BigDecimal freeQuantity,
    @Schema(
            description = "True when the unclamped free quantity is negative",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean overCommitted,
    List<SalesLotIntentDto> intents,
    List<SalesLotPieceDto> pieces) {}
