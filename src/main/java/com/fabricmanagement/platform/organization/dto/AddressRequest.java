package com.fabricmanagement.platform.organization.dto;

import com.fabricmanagement.platform.communication.domain.AddressType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nested DTO for adding an address when creating an organization. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

  private String streetAddress;
  private String city;
  private String state;
  private String postalCode;
  private String country;

  @NotNull(message = "Address type is required")
  private AddressType addressType;

  private Boolean isPrimary;
  private String contactPhone;
  private String contactEmail;
  private String contactPerson;
}
