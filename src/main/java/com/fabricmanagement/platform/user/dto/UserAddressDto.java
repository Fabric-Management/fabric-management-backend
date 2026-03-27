package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.user.domain.UserAddress;
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

  public static UserAddressDto from(UserAddress userAddress) {
    if (userAddress == null) {
      return null;
    }
    return UserAddressDto.builder()
        .uid(userAddress.getUid())
        .userId(userAddress.getUserId())
        .addressId(userAddress.getAddressId())
        .address(
            userAddress.getAddress() != null ? AddressDto.from(userAddress.getAddress()) : null)
        .isPrimary(userAddress.getIsPrimary())
        .isWorkAddress(userAddress.getIsWorkAddress())
        .build();
  }
}
