package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.util.validation.LandlineNumber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyWithContactRequest {

  @NotBlank(message = "Company name is required")
  private String companyName;

  @NotBlank(message = "Tax ID is required")
  private String taxId;

  @NotNull(message = "Company type is required")
  private CompanyType companyType;

  private UUID parentCompanyId;

  /** Nested contacts (0..N). When non-empty, used instead of flat email/phone. */
  @Valid @Builder.Default private List<ContactRequest> contacts = new ArrayList<>();

  /** Nested addresses (0..N). When non-empty, used instead of flat address/city/country. */
  @Valid @Builder.Default private List<AddressRequest> addresses = new ArrayList<>();

  /**
   * @deprecated Prefer {@link #contacts}. Used when contacts list is empty.
   */
  @Deprecated
  @Email(
      message = "Invalid email format",
      regexp = "^$|^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
  private String email;

  /**
   * @deprecated Prefer {@link #contacts}. Used when contacts list is empty. Phone in E.164 (e.g.,
   *     +905551234567).
   */
  @Deprecated
  @LandlineNumber(message = "Invalid landline number format. Example: +44 20 7123 4567")
  private String phoneNumber;

  /**
   * @deprecated Prefer {@link #addresses}. Used when addresses list is empty.
   */
  @Deprecated private String address;

  /**
   * @deprecated Prefer {@link #addresses}.
   */
  @Deprecated private String city;

  /**
   * @deprecated Prefer {@link #addresses}.
   */
  @Deprecated private String state;

  /**
   * @deprecated Prefer {@link #addresses}.
   */
  @Deprecated private String postalCode;

  /**
   * @deprecated Prefer {@link #addresses}.
   */
  @Deprecated private String country;
}
