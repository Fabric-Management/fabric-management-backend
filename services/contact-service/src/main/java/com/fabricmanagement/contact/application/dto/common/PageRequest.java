package com.fabricmanagement.contact.application.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest as SpringPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Request DTO for pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {
    
    @Builder.Default
    private int page = 0;
    
    @Builder.Default
    private int size = 20;
    
    private String sortBy;
    private String sortDirection;
    
    public Pageable toPageable() {
        if (sortBy != null && sortDirection != null) {
            Sort.Direction direction = Sort.Direction.fromString(sortDirection);
            Sort sort = Sort.by(direction, sortBy);
            return SpringPageRequest.of(page, size, sort);
        }
        return SpringPageRequest.of(page, size);
    }
}
