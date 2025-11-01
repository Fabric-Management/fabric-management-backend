package com.fabricmanagement.common.platform.communication.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for address autocomplete.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutocompleteRequest {

    @NotBlank(message = "Input is required")
    private String input;

    /**
     * Optional: Country code for component restriction (e.g., "tr", "gb")
     */
    private String country;
}

