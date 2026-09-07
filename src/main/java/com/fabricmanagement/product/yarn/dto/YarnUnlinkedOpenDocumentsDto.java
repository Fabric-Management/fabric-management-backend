package com.fabricmanagement.product.yarn.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Advisory counts of open yarn documents without a product link")
public record YarnUnlinkedOpenDocumentsDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long openPurchaseOrders,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long openWorkOrders) {}
