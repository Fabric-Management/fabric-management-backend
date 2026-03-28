package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.communication.dto.AddressDto;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for user address assignment. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddressDto {
  private String uid;
  private UUID userId;
  private UUID addressId;
  private AddressDto address;
  private Boolean isPrimary;
  private Boolean isWorkAddress;
}
