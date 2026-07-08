package com.fabricmanagement.sales.quote.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteLineLotPieceSnapshot(
    UUID stockUnitId, String pieceNo, BigDecimal measure, String unit) {}
