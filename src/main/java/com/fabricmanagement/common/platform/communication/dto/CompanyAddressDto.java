package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.CompanyAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyAddressDto {

    private UUID companyId;
    private UUID addressId;
    private AddressDto address;
    private Boolean isPrimary;
    private Boolean isHeadquarters;

    public static CompanyAddressDto from(CompanyAddress companyAddress) {
        return CompanyAddressDto.builder()
            .companyId(companyAddress.getCompanyId())
            .addressId(companyAddress.getAddressId())
            .address(companyAddress.getAddress() != null ? AddressDto.from(companyAddress.getAddress()) : null)
            .isPrimary(companyAddress.getIsPrimary())
            .isHeadquarters(companyAddress.getIsHeadquarters())
            .build();
    }
}

