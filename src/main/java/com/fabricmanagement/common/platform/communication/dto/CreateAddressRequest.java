package com.fabricmanagement.common.platform.communication.dto;

import com.fabricmanagement.common.platform.communication.domain.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddressRequest {

    @NotBlank(message = "Street address is required")
    private String streetAddress;

    @NotBlank(message = "City is required")
    private String city;

    private String state;

    private String postalCode;

    @NotBlank(message = "Country is required")
    private String country;

    @NotNull(message = "Address type is required")
    private AddressType addressType;

    private String label;
}

