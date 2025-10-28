package com.fabricmanagement.production.execution.fiber.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request for quantity operations (reserve, release, consume).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuantityRequest {
    
    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.001", message = "Quantity must be at least 0.001")
    private BigDecimal quantity;
}

