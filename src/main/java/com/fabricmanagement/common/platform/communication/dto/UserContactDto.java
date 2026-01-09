package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.UserContact;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for user contact assignment. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContactDto {
  private String
      uid; // Human-readable identifier (BaseJunctionEntity uses composite key, no id field)
  private UUID userId;
  private UUID contactId;
  private ContactDto contact;
  private Boolean isDefault;
  @Deprecated private Boolean isWhatsApp;

  public static UserContactDto from(UserContact userContact) {
    if (userContact == null) {
      return null;
    }

    ContactDto contactDto =
        userContact.getContact() != null ? ContactDto.from(userContact.getContact()) : null;
    return UserContactDto.builder()
        .uid(userContact.getUid())
        .userId(userContact.getUserId())
        .contactId(userContact.getContactId())
        .contact(contactDto)
        .isDefault(userContact.getIsDefault())
        .isWhatsApp(contactDto != null ? contactDto.getIsWhatsApp() : null)
        .build();
  }
}
