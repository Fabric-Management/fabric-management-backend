package com.fabricmanagement.company.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Department Updated Event
 * 
 * Published when a department is updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentUpdatedEvent {
    
    private UUID departmentId;
    private UUID companyId;
    private String code;
    private String name;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}

