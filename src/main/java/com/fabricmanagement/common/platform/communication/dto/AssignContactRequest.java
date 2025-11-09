package com.fabricmanagement.common.platform.communication.dto;

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
public class AssignContactRequest {

    @NotNull(message = "Contact ID is required")
    private UUID contactId;

    @Builder.Default
    private Boolean isDefault = false;

    @Deprecated
    @Builder.Default
    private Boolean isForAuthentication = false;  // Deprecated: Authentication relies on verified contacts
    private String department;            // For CompanyContact only
}

