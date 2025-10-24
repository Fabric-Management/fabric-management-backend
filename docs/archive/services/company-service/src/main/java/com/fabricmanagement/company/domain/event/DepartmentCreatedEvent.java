package com.fabricmanagement.company.domain.event;

import com.fabricmanagement.shared.domain.policy.DepartmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Department Created Event
 * 
 * Published when a new department is created.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCreatedEvent {
    
    private UUID departmentId;
    private UUID companyId;
    private String code;
    private String name;
    private DepartmentType type;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}

