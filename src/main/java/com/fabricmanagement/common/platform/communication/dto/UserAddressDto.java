package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.UserAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for user address assignment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddressDto {
    private String uid; // Human-readable identifier (BaseJunctionEntity uses composite key, no id field)
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
            .address(userAddress.getAddress() != null 
                ? AddressDto.from(userAddress.getAddress()) 
                : null)
            .isPrimary(userAddress.getIsPrimary())
            .isWorkAddress(userAddress.getIsWorkAddress())
            .build();
    }
}
