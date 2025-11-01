package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.UserContact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContactDto {

    private UUID userId;
    private UUID contactId;
    private ContactDto contact;
    private Boolean isDefault;
    private Boolean isForAuthentication;

    public static UserContactDto from(UserContact userContact) {
        return UserContactDto.builder()
            .userId(userContact.getUserId())
            .contactId(userContact.getContactId())
            .contact(userContact.getContact() != null ? ContactDto.from(userContact.getContact()) : null)
            .isDefault(userContact.getIsDefault())
            .isForAuthentication(userContact.getIsForAuthentication())
            .build();
    }
}

