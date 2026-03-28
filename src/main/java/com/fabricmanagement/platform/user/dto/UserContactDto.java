package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.communication.dto.ContactDto;
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
  private String uid;
  private UUID userId;
  private UUID contactId;
  private ContactDto contact;
  private Boolean isDefault;
}
