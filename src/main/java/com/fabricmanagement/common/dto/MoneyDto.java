package com.fabricmanagement.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Data Transfer Object for monetary values.
 *
 * <p>NOTE: When using @JsonUnwrapped with MoneyDto, ensure only ONE Money field is unwrapped per
 * DTO, or use the 'prefix' attribute (e.g. @JsonUnwrapped(prefix = "unit_")) to prevent column
 * collisions.
 */
public record MoneyDto(
    @NotNull BigDecimal amount, @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}
