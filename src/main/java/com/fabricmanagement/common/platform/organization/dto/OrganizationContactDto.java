package com.fabricmanagement.common.platform.organization.dto;

import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.organization.domain.OrganizationContact;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for OrganizationContact junction entity. Includes nested contact details. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationContactDto {

  private UUID organizationId;
  private UUID contactId;
  private Boolean isDefault;
  private String department;

  /** Nested contact details — populated when the contact relation is loaded. */
  private ContactData contact;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ContactData {
    private UUID id;
    private String contactValue;
    private ContactType contactType;
    private String label;
    private Boolean isVerified;
    private Boolean isPersonal;
    private UUID parentContactId;
  }

  public static OrganizationContactDto from(OrganizationContact entity) {
    OrganizationContactDtoBuilder builder =
        OrganizationContactDto.builder()
            .organizationId(entity.getOrganizationId())
            .contactId(entity.getContactId())
            .isDefault(entity.getIsDefault())
            .department(entity.getDepartment());

    Contact contact = entity.getContact();
    if (contact != null) {
      builder.contact(
          ContactData.builder()
              .id(contact.getId())
              .contactValue(contact.getContactValue())
              .contactType(contact.getContactType())
              .label(contact.getLabel())
              .isVerified(contact.getIsVerified())
              .isPersonal(contact.getIsPersonal())
              .parentContactId(contact.getParentContactId())
              .build());
    }

    return builder.build();
  }
}
