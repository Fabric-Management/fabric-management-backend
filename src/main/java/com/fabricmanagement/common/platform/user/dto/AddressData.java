package com.fabricmanagement.common.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Address data for user creation.
 *
 * <p>Used in CreateInternalUserRequest and CreateExternalUserRequest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressData {
  @NotBlank(message = "Street address is required")
  private String streetAddress;

  @NotBlank(message = "City is required")
  private String city;

  private String state;

  private String postalCode;

  @NotBlank(message = "Country is required")
  private String country;

  /**
   * Google Maps Place ID for address validation (optional). If provided, address will be validated
   * and normalized.
   */
  private String placeId;

  /** Address type: WORK, HOME, etc. Default: WORK */
  @Builder.Default private String addressType = "WORK";

  /** Label for this address (e.g., "Head Office", "Home Address") */
  private String label;

  /** Whether this is the primary address. Default: false (first address becomes primary) */
  @Builder.Default private Boolean isPrimary = false;
}
