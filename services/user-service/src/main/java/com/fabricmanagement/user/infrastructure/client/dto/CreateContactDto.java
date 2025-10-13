package com.fabricmanagement.user.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContactDto {

    private String ownerId;
    private String ownerType;
    private String contactType;
    private String contactValue;

    @JsonProperty("isPrimary")
    private boolean isPrimary;

    @JsonProperty("autoVerified")
    private boolean autoVerified;
}

