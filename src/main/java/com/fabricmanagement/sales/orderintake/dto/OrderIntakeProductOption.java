package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A product the order's customer may order, with the units and widths it is sold in. */
@Schema(name = "OrderIntakeProductOption")
public record OrderIntakeProductOption(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String uid,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProductType productType,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String baseUnit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> allowedUnits,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductSalesDefinitionDto.FinishedWidthOption> finishedWidths,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "True when the catalogue entry is private to this customer")
        boolean customerSpecific,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal listPrice,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String currency) {}
