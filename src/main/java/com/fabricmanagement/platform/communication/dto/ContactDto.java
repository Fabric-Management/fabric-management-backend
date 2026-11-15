package com.fabricmanagement.platform.communication.dto;

import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
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
public class ContactDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private String contactValue;
  private ContactType contactType;
  private Boolean isVerified;
  private UUID parentContactId;
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
        .parentContactId(contact.getParentContactId())
        .isActive(contact.getIsActive())
        .createdAt(contact.getCreatedAt())
        .updatedAt(contact.getUpdatedAt())
        .build();
  }
}
