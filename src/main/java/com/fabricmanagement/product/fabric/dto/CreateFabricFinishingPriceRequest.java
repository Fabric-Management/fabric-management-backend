package com.fabricmanagement.product.fabric.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "CreateFabricFinishingPriceRequest")
public record CreateFabricFinishingPriceRequest(
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID processId,
    @Schema(
            nullable = true,
            description = "The firm the tariff belongs to; leave out for the general price")
        UUID partnerId,
    @NotNull
        @DecimalMin("0")
        @Digits(integer = 11, fraction = 4)
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "1.8500",
            description = "Per the process's charge unit: metre or kilogram")
        BigDecimal unitPrice,
    @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "GBP")
        String currency,
    @NotNull
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "The first day the price applies; a later price replaces it from its date")
        LocalDate validFrom) {}
