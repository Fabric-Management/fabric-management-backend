package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.AddressType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAddressRequest {

  private String streetAddress;
  private String city;
  private String state;
  private String district;
  private String postalCode;
  private String country;
  private String countryCode;
  private AddressType addressType;
  private String label;
  private String contactPerson;
  private String contactPhone;
  private String contactEmail;
}
