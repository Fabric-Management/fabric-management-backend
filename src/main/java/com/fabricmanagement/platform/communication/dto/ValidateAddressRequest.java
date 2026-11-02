package com.fabricmanagement.platform.communication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for address validation.
 *
 * <p>Can validate either by placeId (recommended) or by address string.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateAddressRequest {

  /** Google Places ID (recommended method) */
  @Size(max = 255)
  @Schema(
      description = "Google lookup reference. Recognition is separate from delivery verification.",
      maxLength = 255)
  private String placeId;

  /** Address string (alternative method if placeId not available) */
  @Size(max = 1000)
  @Schema(description = "address", maxLength = 1000)
  private String address;

  /**
   * Original input from autocomplete (optional). Used to extract flat numbers when Google
   * normalizes them away (e.g., "20/34" -> "No:20"). Example: "Flat 4, 14 Mill Lane"
   */
  @Size(max = 1000)
  @Schema(description = "originalInput", maxLength = 1000)
  private String originalInput;

  /** Address type for context (HOME, WORK, HEADQUARTERS, etc.) */
  @Size(max = 50)
  @Schema(description = "addressType", maxLength = 50)
  private String addressType;

  /** Label for the address */
  @Size(max = 100)
  @Schema(
      description = "Location label; required when persisting a lookup result.",
      maxLength = 100)
  private String label;
}
