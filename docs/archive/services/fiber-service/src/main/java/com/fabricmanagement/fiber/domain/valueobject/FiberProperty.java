package com.fabricmanagement.fiber.domain.valueobject;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FiberProperty {
    
    private BigDecimal stapleLength;
    private BigDecimal fineness;
    private BigDecimal tenacity;
    private BigDecimal moistureRegain;
    private String color;
}

