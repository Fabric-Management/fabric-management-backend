package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.UserAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddressDto {

    private UUID userId;
    private UUID addressId;
    private AddressDto address;
    private Boolean isPrimary;
    private Boolean isWorkAddress;

    public static UserAddressDto from(UserAddress userAddress) {
        return UserAddressDto.builder()
            .userId(userAddress.getUserId())
            .addressId(userAddress.getAddressId())
            .address(userAddress.getAddress() != null ? AddressDto.from(userAddress.getAddress()) : null)
            .isPrimary(userAddress.getIsPrimary())
            .isWorkAddress(userAddress.getIsWorkAddress())
            .build();
    }
}

