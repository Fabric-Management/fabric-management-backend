package com.fabricmanagement.common.platform.communication.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for assigning a contact to an address.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignAddressContactRequest {

    @NotNull(message = "Contact ID is required")
    private UUID contactId;

    /**
     * Whether this is the primary contact for the address.
     * Only one contact per address can be primary.
     */
    private Boolean isPrimary;

    /**
     * Optional label for this contact (e.g., "Main Phone", "Reception", "Emergency Contact").
     */
    private String label;
}

