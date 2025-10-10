package com.fabricmanagement.contact.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckContactAvailabilityRequest {
    
    @NotBlank(message = "Contact value is required")
    private String contactValue;
}

