package com.fabricmanagement.contact.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contact Availability Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactAvailabilityResponse {
    
    private String contactValue;
    private boolean available;
}
