package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.Position;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private UUID departmentId;
    private String departmentName;
    private String positionName;
    private String positionCode;
    private String description;
    private UUID defaultRoleId;
    private String defaultRoleName;
    private UUID hierarchicalParentId;
    private String hierarchicalParentName;
    private Integer displayOrder;
    private Boolean isActive;

    public static PositionDto from(Position position) {
        return PositionDto.builder()
            .id(position.getId())
            .tenantId(position.getTenantId())
            .uid(position.getUid())
            .departmentId(position.getDepartmentId())
            .departmentName(position.getDepartment() != null ? position.getDepartment().getDepartmentName() : null)
            .positionName(position.getPositionName())
            .positionCode(position.getPositionCode())
            .description(position.getDescription())
            .defaultRoleId(position.getDefaultRole() != null ? position.getDefaultRole().getId() : null)
            .defaultRoleName(position.getDefaultRole() != null ? position.getDefaultRole().getRoleName() : null)
            .hierarchicalParentId(position.getHierarchicalParent() != null ? position.getHierarchicalParent().getId() : null)
            .hierarchicalParentName(position.getHierarchicalParent() != null ? position.getHierarchicalParent().getPositionName() : null)
            .displayOrder(position.getDisplayOrder())
            .isActive(position.getIsActive())
            .build();
    }
}

