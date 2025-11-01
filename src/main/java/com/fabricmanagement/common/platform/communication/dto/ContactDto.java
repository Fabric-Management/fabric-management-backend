package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
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
public class ContactDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String contactValue;
    private ContactType contactType;
    private Boolean isVerified;
    private Boolean isPrimary;
    private String label;
    private UUID parentContactId;
    private Boolean isPersonal;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static ContactDto from(Contact contact) {
        return ContactDto.builder()
            .id(contact.getId())
            .tenantId(contact.getTenantId())
            .uid(contact.getUid())
            .contactValue(contact.getContactValue())
            .contactType(contact.getContactType())
            .isVerified(contact.getIsVerified())
            .isPrimary(contact.getIsPrimary())
            .label(contact.getLabel())
            .parentContactId(contact.getParentContactId())
            .isPersonal(contact.getIsPersonal())
            .isActive(contact.getIsActive())
            .createdAt(contact.getCreatedAt())
            .updatedAt(contact.getUpdatedAt())
            .build();
    }
}

