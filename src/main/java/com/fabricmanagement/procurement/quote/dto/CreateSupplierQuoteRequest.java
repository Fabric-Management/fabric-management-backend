package com.fabricmanagement.procurement.quote.dto;

import com.fabricmanagement.procurement.quote.domain.QuoteEntryMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Request to create a new supplier quote")
public record CreateSupplierQuoteRequest(
    @NotNull(message = "RFQ ID is required")
        @Schema(description = "RFQ ID", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID rfqId,
    @NotNull(message = "Trading partner ID is required")
        @Schema(
            description = "Supplier trading partner ID",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID tradingPartnerId,
    @NotNull(message = "Valid-until date is required")
        @FutureOrPresent(message = "Valid-until date must be today or in the future")
        @Schema(description = "Quote validity date", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate validUntil,
    @NotBlank(message = "Currency is required")
        @Schema(
            description = "Currency code",
            example = "TRY",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,
    @Schema(description = "Payment terms") String paymentTerms,
    @Schema(description = "Lead time in days") Integer leadTimeDays,
    @NotNull(message = "Entry method is required")
        @Schema(description = "Entry method", requiredMode = Schema.RequiredMode.REQUIRED)
        QuoteEntryMethod entryMethod,
    @Schema(description = "Additional notes") String notes) {}
