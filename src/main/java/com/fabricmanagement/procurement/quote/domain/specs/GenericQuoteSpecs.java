package com.fabricmanagement.procurement.quote.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Generic quote specs for undefined or basic modules")
public record GenericQuoteSpecs(
    @Schema(description = "Any additional processing or requirement notes") String notes)
    implements SupplierQuoteSpecs {}
