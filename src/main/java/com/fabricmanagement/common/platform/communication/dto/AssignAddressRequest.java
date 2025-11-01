package com.fabricmanagement.common.platform.communication.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignAddressRequest {

    @NotNull(message = "Address ID is required")
    private UUID addressId;

    @Builder.Default
    private Boolean isPrimary = false;

    private Boolean isWorkAddress;    // For UserAddress only
    private Boolean isHeadquarters;   // For CompanyAddress only
}

