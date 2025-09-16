package com.fabricmanagement.common.core.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filter criteria for dynamic querying.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterCriteria {

    @NotBlank
    private String field;

    @NotNull
    private FilterOperation operation;

    private Object value;

    private Object secondValue; // For BETWEEN operations

    public enum FilterOperation {
        EQUALS,
        NOT_EQUALS,
        LIKE,
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        BETWEEN,
        IN,
        NOT_IN,
        IS_NULL,
        IS_NOT_NULL
    }
}
