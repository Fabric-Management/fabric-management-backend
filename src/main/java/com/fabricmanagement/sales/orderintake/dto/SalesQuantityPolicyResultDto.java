package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.common.dto.SalesQuantityTermsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "SalesQuantityPolicyResult")
public record SalesQuantityPolicyResultDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long orderVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesQuantityTermsDto quantityTerms) {}
