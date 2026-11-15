package com.fabricmanagement.platform.organization.dto;

import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.domain.OrganizationContact;
import io.swagger.v3.oas.annotations.media.Schema;
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
  private String label;
  private Boolean isPersonal;

  /** Nested contact details — populated when the contact relation is loaded. */
  private ContactData contact;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "OrganizationContactData")
  public static class ContactData {
    private UUID id;
    private String contactValue;
    private ContactType contactType;
    private Boolean isVerified;
    private UUID parentContactId;
  }

  public static OrganizationContactDto from(OrganizationContact entity) {
    OrganizationContactDtoBuilder builder =
        OrganizationContactDto.builder()
            .organizationId(entity.getOrganizationId())
            .contactId(entity.getContactId())
            .isDefault(entity.getIsDefault())
            .department(entity.getDepartment())
            .label(entity.getLabel())
            .isPersonal(entity.getIsPersonal());

    Contact contact = entity.getContact();
    if (contact != null) {
      builder.contact(
          ContactData.builder()
              .id(contact.getId())
              .contactValue(contact.getContactValue())
              .contactType(contact.getContactType())
              .isVerified(contact.getIsVerified())
              .parentContactId(contact.getParentContactId())
              .build());
    }

    return builder.build();
  }
}
