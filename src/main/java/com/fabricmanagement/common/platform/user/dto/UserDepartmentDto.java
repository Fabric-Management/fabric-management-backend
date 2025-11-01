package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.UserDepartment;
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
public class UserDepartmentDto {

    private UUID userId;
    private UUID departmentId;
    private Boolean isPrimary;
    private Instant assignedAt;
    private UUID assignedBy;

    public static UserDepartmentDto from(UserDepartment userDepartment) {
        return UserDepartmentDto.builder()
            .userId(userDepartment.getUserId())
            .departmentId(userDepartment.getDepartmentId())
            .isPrimary(userDepartment.getIsPrimary())
            .assignedAt(userDepartment.getAssignedAt())
            .assignedBy(userDepartment.getAssignedBy())
            .build();
    }
}

