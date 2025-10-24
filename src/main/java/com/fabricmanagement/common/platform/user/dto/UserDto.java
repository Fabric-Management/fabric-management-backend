package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.ContactType;
import com.fabricmanagement.common.platform.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String firstName;
    private String lastName;
    private String displayName;
    private String contactValue;
    private ContactType contactType;
    private UUID companyId;
    private String department;
    private Boolean isActive;
    private Instant lastActiveAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserDto from(User user) {
        return UserDto.builder()
            .id(user.getId())
            .tenantId(user.getTenantId())
            .uid(user.getUid())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .displayName(user.getDisplayName())
            .contactValue(user.getContactValue())
            .contactType(user.getContactType())
            .companyId(user.getCompanyId())
            .department(user.getDepartment())
            .isActive(user.getIsActive())
            .lastActiveAt(user.getLastActiveAt())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
    }
}

