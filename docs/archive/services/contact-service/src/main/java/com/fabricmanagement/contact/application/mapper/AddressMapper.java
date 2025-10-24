package com.fabricmanagement.contact.application.mapper;

import com.fabricmanagement.contact.api.dto.request.CreateAddressRequest;
import com.fabricmanagement.contact.api.dto.response.AddressResponse;
import com.fabricmanagement.contact.domain.entity.Address;
import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.domain.valueobject.AddressType;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Address Mapper
 * 
 * Handles DTO ↔ Entity mapping for Address
 * NO business logic - pure mapping only
 */
@Component
public class AddressMapper {

    /**
     * Maps CreateAddressRequest → Address entity
     */
    public Address fromCreateRequest(CreateAddressRequest request, UUID contactId) {
        return Address.builder()
                .contactId(contactId)
                .ownerId(UUID.fromString(request.getOwnerId()))
                .ownerType(Contact.OwnerType.valueOf(request.getOwnerType()))
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .district(request.getDistrict())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .googlePlaceId(request.getGooglePlaceId())
                .addressType(request.getAddressType() != null 
                    ? AddressType.valueOf(request.getAddressType()) 
                    : AddressType.HOME)
                .isPrimary(request.getIsPrimary())
                .isVerified(false)
                .build();
    }

    /**
     * Maps Address entity → AddressResponse
     */
    public AddressResponse toResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .contactId(address.getContactId())
                .ownerId(address.getOwnerId().toString())
                .ownerType(address.getOwnerType().name())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .district(address.getDistrict())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .googlePlaceId(address.getGooglePlaceId())
                .formattedAddress(address.getFormattedAddress())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .addressType(address.getAddressType() != null ? address.getAddressType().name() : null)
                .isPrimary(address.isPrimary())
                .isVerified(address.isVerified())
                .verifiedAt(address.getVerifiedAt())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    /**
     * Updates existing Address entity from request
     */
    public void updateFromRequest(Address address, CreateAddressRequest request) {
        address.setAddressLine1(request.getAddressLine1());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setDistrict(request.getDistrict());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        address.setGooglePlaceId(request.getGooglePlaceId());
        
        if (request.getAddressType() != null) {
            address.setAddressType(AddressType.valueOf(request.getAddressType()));
        }
    }
}

