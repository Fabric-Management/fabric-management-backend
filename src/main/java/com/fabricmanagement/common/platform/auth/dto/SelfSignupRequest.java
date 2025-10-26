package com.fabricmanagement.common.platform.auth.dto;

import com.fabricmanagement.common.platform.company.domain.CompanyType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Self-service signup request - Public signup flow.
 *
 * <p>Used by users signing up from the website.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelfSignupRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Tax ID is required")
    private String taxId;

    @NotNull(message = "Company type is required")
    private CompanyType companyType;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private List<String> selectedOS;

    @Builder.Default
    private Boolean acceptedTerms = false;
}

