package com.fabricmanagement.common.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageRequest(
        @Min(0)
        Integer page,

        @Min(1)
        @Max(100)
        Integer size,

        String sortBy,

        String sortDirection
) {
    // Default constructor with default values
    public PageRequest() {
        this(0, 20, "id", "ASC");
    }
}