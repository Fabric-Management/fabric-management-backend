package com.fabricmanagement.common.platform.organization.dto;

import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.organization.domain.OrganizationAddress;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for OrganizationAddress junction entity. Includes nested address details. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationAddressDto {

  private UUID organizationId;
  private UUID addressId;
  private Boolean isPrimary;
  private Boolean isHeadquarters;

  /** Nested address details — populated when the address relation is loaded. */
  private AddressData address;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AddressData {
    private UUID id;
    private String streetAddress;
    private String city;
    private String state;
    private String district;
    private String postalCode;
    private String country;
    private String countryCode;
    private AddressType addressType;
    private String label;
    private String formattedAddress;
    private String contactPhone;
    private String contactEmail;
    private String contactPerson;
    private Double latitude;
    private Double longitude;
  }

  public static OrganizationAddressDto from(OrganizationAddress entity) {
    OrganizationAddressDtoBuilder builder =
        OrganizationAddressDto.builder()
            .organizationId(entity.getOrganizationId())
            .addressId(entity.getAddressId())
            .isPrimary(entity.getIsPrimary())
            .isHeadquarters(entity.getIsHeadquarters());

    Address address = entity.getAddress();
    if (address != null) {
      builder.address(
          AddressData.builder()
              .id(address.getId())
              .streetAddress(address.getStreetAddress())
              .city(address.getCity())
              .state(address.getState())
              .district(address.getDistrict())
              .postalCode(address.getPostalCode())
              .country(address.getCountry())
              .countryCode(address.getCountryCode())
              .addressType(address.getAddressType())
              .label(address.getLabel())
              .formattedAddress(address.getFormattedAddress())
              .contactPhone(address.getContactPhone())
              .contactEmail(address.getContactEmail())
              .contactPerson(address.getContactPerson())
              .latitude(address.getLatitude())
              .longitude(address.getLongitude())
              .build());
    }

    return builder.build();
  }
}
