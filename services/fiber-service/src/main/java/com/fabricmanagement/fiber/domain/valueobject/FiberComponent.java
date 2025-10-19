package com.fabricmanagement.fiber.domain.valueobject;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FiberComponent {
    
    private String fiberCode;
    private BigDecimal percentage;
    
    @Enumerated(EnumType.STRING)
    private SustainabilityType sustainabilityType;
}

