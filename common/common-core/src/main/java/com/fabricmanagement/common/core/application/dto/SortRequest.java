package com.fabricmanagement.common.core.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sort request for ordering query results.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SortRequest {

    @NotBlank
    private String property;

    @NotNull
    @Builder.Default
    private SortDirection direction = SortDirection.ASC;

    /**
     * Converts to Spring's Sort.Order.
     */
    public org.springframework.data.domain.Sort.Order toOrder() {
        return new org.springframework.data.domain.Sort.Order(
            direction == SortDirection.ASC ?
                org.springframework.data.domain.Sort.Direction.ASC :
                org.springframework.data.domain.Sort.Direction.DESC,
            property
        );
    }

    public enum SortDirection {
        ASC, DESC
    }
}
