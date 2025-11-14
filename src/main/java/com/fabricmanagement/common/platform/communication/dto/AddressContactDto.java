package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.AddressContact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for address-contact assignment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressContactDto {
    private String uid; // Human-readable identifier (BaseJunctionEntity uses composite key, no id field)
    private UUID addressId;
    private UUID contactId;
    private ContactDto contact;
    private Boolean isPrimary;
    private String label;

    public static AddressContactDto from(AddressContact addressContact) {
        if (addressContact == null) {
            return null;
        }

        ContactDto contactDto = addressContact.getContact() != null ? ContactDto.from(addressContact.getContact()) : null;
        return AddressContactDto.builder()
            .uid(addressContact.getUid())
            .addressId(addressContact.getAddressId())
            .contactId(addressContact.getContactId())
            .contact(contactDto)
            .isPrimary(addressContact.getIsPrimary())
            .label(addressContact.getLabel())
            .build();
    }
}

