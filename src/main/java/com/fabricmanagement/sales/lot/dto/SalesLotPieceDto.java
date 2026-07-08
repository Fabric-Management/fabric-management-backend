package com.fabricmanagement.sales.lot.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesLotPieceDto(
    UUID id,
    String pieceNo,
    String packageType,
    BigDecimal length,
    String lengthUnit,
    BigDecimal weight,
    String weightUnit,
    String status,
    long softReservedCount) {}
