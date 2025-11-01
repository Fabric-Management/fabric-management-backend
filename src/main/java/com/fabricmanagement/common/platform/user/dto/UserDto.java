package com.fabricmanagement.common.platform.user.dto;

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
    private UUID companyId;
    private UUID roleId;
    private String role; // Role name for display
    private Boolean isActive;
    private Instant lastActiveAt;
    private Instant onboardingCompletedAt;
    private Boolean hasCompletedOnboarding;
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
            .companyId(user.getCompanyId())
            .roleId(user.getRole() != null ? user.getRole().getId() : null)
            .role(user.getRole() != null ? user.getRole().getRoleName() : null)
            .isActive(user.getIsActive())
            .lastActiveAt(user.getLastActiveAt())
            .onboardingCompletedAt(user.getOnboardingCompletedAt())
            .hasCompletedOnboarding(user.hasCompletedOnboarding())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
    }
}

