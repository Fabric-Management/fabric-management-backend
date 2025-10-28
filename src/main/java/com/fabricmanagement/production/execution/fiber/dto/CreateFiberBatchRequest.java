package com.fabricmanagement.production.execution.fiber.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Request for creating a fiber batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFiberBatchRequest {
    
    @NotNull(message = "Fiber ID is required")
    private UUID fiberId;
    
    @NotBlank(message = "Batch code is required")
    private String batchCode;
    
    private String supplierBatchCode;
    
    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
    private BigDecimal quantity;
    
    @NotBlank(message = "Unit is required")
    private String unit;
    
    private Instant productionDate;
    
    private Instant expiryDate;
    
    private String warehouseLocation;
    
    private String remarks;
}

