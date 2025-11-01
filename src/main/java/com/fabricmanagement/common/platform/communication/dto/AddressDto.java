package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String streetAddress;
    private String city;
    private String state;
    private String district;
    private String postalCode;
    private String country;
    private String countryCode;
    private AddressType addressType;
    private Boolean isPrimary;
    private String label;
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
            .city(address.getCity())
            .state(address.getState())
            .district(address.getDistrict())
            .postalCode(address.getPostalCode())
            .country(address.getCountry())
            .countryCode(address.getCountryCode())
            .addressType(address.getAddressType())
            .isPrimary(address.getIsPrimary())
            .label(address.getLabel())
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

