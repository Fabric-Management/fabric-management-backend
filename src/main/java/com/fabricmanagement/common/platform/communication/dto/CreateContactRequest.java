package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.ContactType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContactRequest {

    /**
     * Contact value (email or phone). Required.
     */
    @NotBlank(message = "Contact value is required")
    private String contactValue;

    /**
     * Optional contact type. If null, backend will infer based on value.
     */
    private ContactType contactType;

    private String label;

    @Builder.Default
    private Boolean isPersonal = true;

    private UUID parentContactId;  // Required for PHONE_EXTENSION
}

