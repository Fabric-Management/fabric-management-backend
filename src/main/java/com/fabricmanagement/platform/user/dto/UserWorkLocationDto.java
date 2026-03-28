package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.communication.dto.AddressDto;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWorkLocationDto {
  private UUID userId;
  private UUID orgAddressId;
  private UUID organizationId;
  private Boolean isPrimary;
  private String notes;
  private AddressDto address;
  private String organizationName;
}
