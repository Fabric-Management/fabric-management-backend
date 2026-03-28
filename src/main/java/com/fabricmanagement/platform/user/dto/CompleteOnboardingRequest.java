package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/common/users/me/onboarding/complete.
 *
 * <p>Carries enrichment data collected by the onboarding wizard. All fields are optional so that:
 *
 * <ul>
 *   <li>Existing callers that send no body continue to work (null request → skip enrichment).
 *   <li>Partial saves are safe — only non-null values overwrite existing organization data.
 * </ul>
 *
 * <p>Validation constraints mirror frontend Zod schema in {@code onboarding.schema.ts}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteOnboardingRequest {

  // ── Organization identity ───────────────────────────────────────────────────

  /** Display / trading name (max 255, matches Organization.name column). */
  @Size(max = 255, message = "Organization name must not exceed 255 characters")
  private String organizationName;

  /** Organization type (e.g. VERTICAL_MILL). Optional; set at registration, may be updated. */
  private OrganizationType organizationType;

  /** Tax / VAT identification number (5–50 chars). */
  @Size(min = 5, max = 50, message = "Tax ID must be between 5 and 50 characters")
  private String taxId;

  // ── Enrichment fields (not in registration form) ────────────────────────────

  /** Legal registered name for invoicing / official documents. */
  @Size(max = 200, message = "Legal name must not exceed 200 characters")
  private String legalName;

  /** Trade registry / chamber registration number. */
  @Size(max = 100, message = "Registration number must not exceed 100 characters")
  private String registrationNumber;

  /** Industry sector (e.g. TEXTILE, LOGISTICS). */
  @Size(max = 100, message = "Industry must not exceed 100 characters")
  private String industry;

  /**
   * Company website URL.
   *
   * <p>Accepts URLs with or without protocol prefix (http/https).
   */
  @Size(max = 500, message = "Website must not exceed 500 characters")
  @Pattern(
      regexp = "^(https?:\\/\\/)?([a-zA-Z0-9.-]+)\\.([a-zA-Z]{2,6})[\\/\\w .-]*\\/?$|^$",
      message = "Invalid website URL")
  private String website;

  /** Short company description (max 2000 chars). */
  @Size(max = 2000, message = "Description must not exceed 2000 characters")
  private String description;

  // ── Headquarters address ─────────────────────────────────────────────────────

  /** Street address line 1 — mapped to Address.streetAddress. */
  @Size(max = 255, message = "Address must not exceed 255 characters")
  private String addressLine1;

  /** Suite, floor, building name etc. — mapped to Address.addressLine2. */
  @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
  private String addressLine2;

  /** City name. */
  @Size(max = 100, message = "City must not exceed 100 characters")
  private String city;

  /** State / province / region. */
  @Size(max = 100, message = "State must not exceed 100 characters")
  private String state;

  /** District / sub-administrative area (e.g. ilçe). */
  @Size(max = 100, message = "District must not exceed 100 characters")
  private String district;

  /** Postal / ZIP code. */
  @Size(max = 20, message = "Postal code must not exceed 20 characters")
  private String postalCode;

  /** Country name or ISO 3166-1 alpha-2/alpha-3 code. */
  @Size(max = 100, message = "Country must not exceed 100 characters")
  private String country;

  /**
   * Location name / label for the headquarters address (e.g. "Headquarters", "Production Plant").
   * If blank or null, backend defaults to "Headquarters".
   */
  @Size(max = 100, message = "Address label must not exceed 100 characters")
  private String addressLabel;

  // ── Company contacts ─────────────────────────────────────────────────────────

  /** Company email address. */
  @Email(message = "Invalid company email format")
  @Size(max = 254, message = "Email must not exceed 254 characters")
  private String companyEmail;

  /** Company phone number (E.164 recommended). */
  @Size(max = 20, message = "Phone must not exceed 20 characters")
  private String companyPhone;

  // ── Platform modules ─────────────────────────────────────────────────────────

  /** Selected OS module codes (e.g. ["FabricOS", "LogiOS"]). */
  private List<String> selectedOS;
}
