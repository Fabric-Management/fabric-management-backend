package com.fabricmanagement.platform.communication.dto;

import com.fabricmanagement.platform.communication.domain.AddressType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddressRequest {

  @NotBlank(message = "Street address is required")
  @Size(max = 500)
  @Schema(
      description = "streetAddress",
      maxLength = 500,
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String streetAddress;

  @NotBlank(message = "City is required")
  @Size(max = 100)
  @Schema(description = "city", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
  private String city;

  @Size(max = 100)
  @Schema(description = "state", maxLength = 100)
  private String state;

  @Size(max = 100)
  @Schema(description = "district", maxLength = 100)
  private String district;

  @Size(max = 20)
  @Schema(description = "postalCode", maxLength = 20)
  private String postalCode;

  @NotBlank(message = "Country is required")
  @Size(max = 100)
  @Schema(description = "country", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
  private String country;

  @Size(max = 2)
  @Schema(
      description =
          "Optional two-letter country code. Omit when unknown; never inferred from UI language.",
      maxLength = 2)
  @Pattern(regexp = "(?:[A-Za-z]{2})?", message = "Country code must contain two letters")
  private String countryCode;

  @Size(max = 255)
  @Schema(
      description =
          "Flat, floor, suite, building or entrance details; kept separate from streetAddress.",
      maxLength = 255)
  private String addressLine2;

  @NotNull(message = "Address type is required")
  private AddressType addressType;

  @NotBlank(message = "Location Name (Label) is required")
  @Size(max = 100)
  @Schema(description = "label", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
  private String label;
}
