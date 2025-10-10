package com.fabricmanagement.contact.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContactRequest {

    @NotBlank(message = "Owner ID is required")
    private String ownerId;

    @NotBlank(message = "Owner type is required")
    private String ownerType; // USER or COMPANY

    @NotBlank(message = "Contact value is required")
    @Size(max = 255, message = "Contact value is too long")
    private String contactValue;

    @NotBlank(message = "Contact type is required")
    private String contactType; // EMAIL, PHONE, ADDRESS

    @NotNull(message = "Primary flag is required")
    @JsonProperty("isPrimary")
    private boolean isPrimary;

    @Builder.Default
    @JsonProperty("autoVerified")
    private boolean autoVerified = false;
}

