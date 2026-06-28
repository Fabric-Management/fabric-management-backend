package com.fabricmanagement.platform.auth.dto;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Self-service signup request - Public signup flow.
 *
 * <p>Used by users signing up from the website.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelfSignupRequest {

  @NotBlank(message = "Organization name is required")
  private String organizationName;

  @NotBlank(message = "Tax ID is required")
  private String taxId;

  @NotNull(message = "Organization type is required")
  private OrganizationType organizationType;

  @NotBlank(message = "First name is required")
  private String firstName;

  @NotBlank(message = "Last name is required")
  private String lastName;

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  private String email;

  private List<String> selectedOS;

  @Builder.Default private SignupIntent intent = SignupIntent.PLAYGROUND;

  @Builder.Default private Boolean acceptedTerms = false;

  public SignupIntent getIntent() {
    return intent != null ? intent : SignupIntent.PLAYGROUND;
  }
}
