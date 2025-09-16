package com.fabricmanagement.common.core.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Standard page request with pagination and sorting parameters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageRequest {

    @NotNull
    @Min(0)
    @Builder.Default
    private Integer page = 0;

    @NotNull
    @Min(1)
    @Builder.Default
    private Integer size = 20;

    private List<SortRequest> sort;

    private List<FilterCriteria> filters;

    /**
     * Converts to Spring's Pageable.
     */
    public org.springframework.data.domain.PageRequest toPageable() {
        return org.springframework.data.domain.PageRequest.of(page, size, toSort());
    }

    /**
     * Converts sort requests to Spring's Sort.
     */
    private org.springframework.data.domain.Sort toSort() {
        if (sort == null || sort.isEmpty()) {
            return org.springframework.data.domain.Sort.unsorted();
        }

        List<org.springframework.data.domain.Sort.Order> orders = sort.stream()
            .map(SortRequest::toOrder)
            .toList();

        return org.springframework.data.domain.Sort.by(orders);
    }
}
