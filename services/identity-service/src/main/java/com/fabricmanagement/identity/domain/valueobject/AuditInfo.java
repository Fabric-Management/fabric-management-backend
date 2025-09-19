package com.fabricmanagement.identity.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Value object for audit information.
 * Encapsulates audit fields from BaseEntity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditInfo {
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
    private Boolean deleted;

    public static AuditInfo create(String createdBy) {
        return AuditInfo.builder()
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .createdBy(createdBy)
            .updatedBy(createdBy)
            .version(0L)
            .deleted(false)
            .build();
    }

    public void update(String updatedBy) {
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = updatedBy;
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }
}