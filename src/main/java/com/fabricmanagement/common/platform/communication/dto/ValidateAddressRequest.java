package com.fabricmanagement.common.platform.communication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for address validation.
 *
 * <p>Can validate either by placeId (recommended) or by address string.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateAddressRequest {

    /**
     * Google Places ID (recommended method)
     */
    private String placeId;

    /**
     * Address string (alternative method if placeId not available)
     */
    private String address;

    /**
     * Address type for context (HOME, WORK, HEADQUARTERS, etc.)
     */
    private String addressType;

    /**
     * Label for the address
     */
    private String label;
}

