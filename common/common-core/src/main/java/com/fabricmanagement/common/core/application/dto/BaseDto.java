package com.fabricmanagement.common.core.application.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Base DTO class providing common fields for all DTOs.
 */
@Getter
@Setter
@SuperBuilder
public abstract class BaseDto {

    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
