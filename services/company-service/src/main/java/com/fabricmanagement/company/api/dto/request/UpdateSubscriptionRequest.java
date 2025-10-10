package com.fabricmanagement.company.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for updating company subscription
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubscriptionRequest {
    
    @NotBlank(message = "Subscription plan is required")
    private String plan; // BASIC, PREMIUM, ENTERPRISE
    
    @NotNull(message = "Max users is required")
    @Min(value = 1, message = "Max users must be at least 1")
    private Integer maxUsers;
    
    @NotNull(message = "End date is required")
    private LocalDateTime endDate;
}

