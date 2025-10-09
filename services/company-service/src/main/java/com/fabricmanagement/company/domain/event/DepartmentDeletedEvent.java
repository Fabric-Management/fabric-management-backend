package com.fabricmanagement.company.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Department Deleted Event
 * 
 * Published when a department is soft deleted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDeletedEvent {
    
    private UUID departmentId;
    private UUID companyId;
    private String code;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}

