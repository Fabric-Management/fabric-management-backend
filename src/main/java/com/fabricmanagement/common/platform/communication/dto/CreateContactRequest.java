package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "Contact value is required")
    private String contactValue;

    @NotNull(message = "Contact type is required")
    private ContactType contactType;

    private String label;

    @Builder.Default
    private Boolean isPersonal = true;

    private UUID parentContactId;  // Required for PHONE_EXTENSION
}

