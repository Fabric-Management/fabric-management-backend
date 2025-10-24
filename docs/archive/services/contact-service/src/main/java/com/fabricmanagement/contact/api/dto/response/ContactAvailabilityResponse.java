package com.fabricmanagement.contact.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactAvailabilityResponse {
    
    private String contactValue;
    private boolean available;
}

