package com.fabricmanagement.fiber.api.dto.request;

import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class FiberComponentDto {
    
    @NotBlank(message = "Fiber code is required")
    private String fiberCode;
    
    @NotNull(message = "Percentage is required")
    @DecimalMin(value = "0.01", message = "Percentage must be greater than 0")
    @DecimalMax(value = "100.00", message = "Percentage must be less than or equal to 100")
    private BigDecimal percentage;
    
    private String sustainabilityType;
}

