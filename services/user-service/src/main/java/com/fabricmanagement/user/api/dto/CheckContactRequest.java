package com.fabricmanagement.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for checking contact availability
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckContactRequest {

    @NotBlank(message = "Contact value is required")
    private String contactValue;  // email or phone
}
