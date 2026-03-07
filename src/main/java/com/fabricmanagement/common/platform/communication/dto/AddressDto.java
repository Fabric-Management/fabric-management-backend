package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private String streetAddress;
  private String addressLine2;
  private String city;
  private String state;
  private String district;
  private String postalCode;
  private String country;
  private String countryCode;
  private AddressType addressType;
  private String label;
  private String contactPhone;
  private String contactEmail;
  private String contactPerson;
  private String formattedAddress;
  private String placeId;
  private Double latitude;
  private Double longitude;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;

  public static AddressDto from(Address address) {
    return AddressDto.builder()
        .id(address.getId())
        .tenantId(address.getTenantId())
        .uid(address.getUid())
        .streetAddress(address.getStreetAddress())
        .addressLine2(address.getAddressLine2())
        .city(address.getCity())
        .state(address.getState())
        .district(address.getDistrict())
        .postalCode(address.getPostalCode())
        .country(address.getCountry())
        .countryCode(address.getCountryCode())
        .addressType(address.getAddressType())
        .label(address.getLabel())
        .contactPhone(address.getContactPhone())
        .contactEmail(address.getContactEmail())
        .contactPerson(address.getContactPerson())
        .formattedAddress(address.getFormattedAddress())
        .placeId(address.getPlaceId())
        .latitude(address.getLatitude())
        .longitude(address.getLongitude())
        .isActive(address.getIsActive())
        .createdAt(address.getCreatedAt())
        .updatedAt(address.getUpdatedAt())
        .build();
  }
}
