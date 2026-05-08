package com.fabricmanagement.procurement.rfq.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Generic RFQ specs for undefined or basic modules")
public record GenericRFQSpecs(
    @Schema(description = "Additional processing or requirement notes") String notes)
    implements SupplierRFQSpecs {}
