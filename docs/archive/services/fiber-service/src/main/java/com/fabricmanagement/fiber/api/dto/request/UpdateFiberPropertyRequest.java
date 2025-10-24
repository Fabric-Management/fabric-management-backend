package com.fabricmanagement.fiber.api.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateFiberPropertyRequest {
    
    private BigDecimal stapleLength;
    private BigDecimal fineness;
    private BigDecimal tenacity;
    private BigDecimal moistureRegain;
    private String color;
    private String sustainabilityType;
}

