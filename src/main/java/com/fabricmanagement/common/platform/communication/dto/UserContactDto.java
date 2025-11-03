package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.UserContact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for user contact assignment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContactDto {
    private String uid; // Human-readable identifier (BaseJunctionEntity uses composite key, no id field)
    private UUID userId;
    private UUID contactId;
    private ContactDto contact;
    private Boolean isDefault;
    private Boolean isForAuthentication;

    public static UserContactDto from(UserContact userContact) {
        if (userContact == null) {
            return null;
        }

        return UserContactDto.builder()
            .uid(userContact.getUid())
            .userId(userContact.getUserId())
            .contactId(userContact.getContactId())
            .contact(userContact.getContact() != null 
                ? ContactDto.from(userContact.getContact()) 
                : null)
            .isDefault(userContact.getIsDefault())
            .isForAuthentication(userContact.getIsForAuthentication())
            .build();
    }
}
