package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.ContactType;
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
public class CreateUserRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Contact value is required")
    private String contactValue;

    @NotNull(message = "Contact type is required")
    private ContactType contactType;

    @NotNull(message = "Company ID is required")
    private UUID companyId;

    private String department;
}

