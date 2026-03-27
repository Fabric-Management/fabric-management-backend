package com.fabricmanagement.platform.communication.dto;

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
  private String placeId;

  /** Address string (alternative method if placeId not available) */
  private String address;

  /**
   * Original input from autocomplete (optional). Used to extract flat numbers when Google
   * normalizes them away (e.g., "20/34" -> "No:20"). Example: "20/34 selvi sokak", "13/2A welsummer
   * grove"
   */
  private String originalInput;

  /** Address type for context (HOME, WORK, HEADQUARTERS, etc.) */
  private String addressType;

  /** Label for the address */
  private String label;
}
