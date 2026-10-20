package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** The shared problem body plus structured form identity; no hold tokens or private owners. */
@Schema(
    name = "DraftStockSelectionProblem",
    allOf = {ApiProblemDetail.class})
public record DraftStockSelectionProblemDto(
    @Schema(nullable = true) UUID clientLineId,
    @Schema(nullable = true) UUID selectionId,
    @Schema(nullable = true, description = "Zero-based index of the existing fabric distribution")
        Integer existingLineIndex,
    @Schema(nullable = true) UUID existingClientLineId,
    @Schema(nullable = true) UUID existingLineId) {}
