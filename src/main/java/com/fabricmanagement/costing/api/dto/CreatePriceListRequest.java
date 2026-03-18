package com.fabricmanagement.costing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Request DTO for creating a new PriceList. */
public record CreatePriceListRequest(
    @NotBlank String name,
    @NotBlank String moduleType,
    @NotBlank String currency,
    @NotNull LocalDate validFrom,
    LocalDate validUntil,
    String seasonTag) {}
