package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String roleName;
    private String roleCode;
    private String description;
    private Boolean isActive;

    public static RoleDto from(Role role) {
        return RoleDto.builder()
            .id(role.getId())
            .tenantId(role.getTenantId())
            .uid(role.getUid())
            .roleName(role.getRoleName())
            .roleCode(role.getRoleCode())
            .description(role.getDescription())
            .isActive(role.getIsActive())
            .build();
    }
}

