package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.GsmBasis;
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
    @Schema(
            nullable = true,
            description =
                "Which metres the price multiplies: what was SENT or what came back (RETURNED,"
                    + " the default)")
        FinishingBillingBasis billingBasis,
    @Schema(
            nullable = true,
            description =
                "The quality grade of the metres (a fabric quality grade); leave out for every"
                    + " grade without a price of its own. A price of 0 makes a grade free.")
        UUID qualityGradeId,
    @Schema(nullable = true, description = "The dyehouse tier; leave out for any")
        UUID priceClassId,
    @Schema(nullable = true, description = "Leave out for any formation") FabricFormation formation,
    @DecimalMin("0")
        @Digits(integer = 6, fraction = 1)
        @Schema(
            nullable = true,
            example = "150.0",
            description = "Lower end of the weight range, inclusive; leave out for no lower end")
        BigDecimal gsmFrom,
    @DecimalMin("0")
        @Digits(integer = 6, fraction = 1)
        @Schema(
            nullable = true,
            example = "200.0",
            description = "Upper end of the weight range, inclusive; leave out for no upper end")
        BigDecimal gsmTo,
    @Schema(
            nullable = true,
            description =
                "Whether the weight range is the greige or the finished weight; required with a"
                    + " range, not allowed without one")
        GsmBasis gsmBasis,
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
