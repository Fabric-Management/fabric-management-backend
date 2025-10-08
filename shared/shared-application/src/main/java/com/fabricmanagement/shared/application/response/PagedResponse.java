package com.fabricmanagement.shared.application.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Paged API Response
 * 
 * Standard paginated response for list endpoints.
 * Combines pagination metadata with standard API response fields.
 * 
 * Usage:
 * <pre>
 * {@code
 * @GetMapping
 * public ResponseEntity<PagedResponse<UserResponse>> listUsers(Pageable pageable) {
 *     PagedResponse<UserResponse> response = userService.listUsersPaginated(pageable);
 *     return ResponseEntity.ok(response);
 * }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {
    
    // ===== Data =====
    
    /**
     * List of items for current page
     */
    private List<T> content;
    
    // ===== Pagination Metadata =====
    
    /**
     * Current page number (0-indexed)
     */
    private int page;
    
    /**
     * Number of items per page
     */
    private int size;
    
    /**
     * Total number of items across all pages
     */
    private long totalElements;
    
    /**
     * Total number of pages
     */
    private int totalPages;
    
    /**
     * Is this the first page?
     */
    private boolean first;
    
    /**
     * Is this the last page?
     */
    private boolean last;
    
    // ===== Standard API Response Fields =====
    
    /**
     * Indicates if the operation was successful
     */
    @Builder.Default
    private Boolean success = true;
    
    /**
     * Human-readable message
     */
    private String message;
    
    /**
     * Response timestamp
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    // ===== Helper Methods =====
    
    /**
     * Check if there is a next page
     */
    public boolean hasNext() {
        return !last;
    }
    
    /**
     * Check if there is a previous page
     */
    public boolean hasPrevious() {
        return !first;
    }
    
    /**
     * Get next page number (null if last page)
     */
    public Integer getNextPage() {
        return hasNext() ? page + 1 : null;
    }
    
    /**
     * Get previous page number (null if first page)
     */
    public Integer getPreviousPage() {
        return hasPrevious() ? page - 1 : null;
    }
    
    // ===== Factory Methods =====
    
    /**
     * Create a successful paged response
     */
    public static <T> PagedResponse<T> of(List<T> content, int page, int size, 
                                          long totalElements, int totalPages) {
        return PagedResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .success(true)
                .message("Data retrieved successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create from Spring Data Page
     */
    public static <T> PagedResponse<T> fromPage(org.springframework.data.domain.Page<T> page) {
        return PagedResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .success(true)
                .message("Data retrieved successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create from Spring Data Page with custom mapper
     */
    public static <T, R> PagedResponse<R> fromPage(
            org.springframework.data.domain.Page<T> page,
            java.util.function.Function<T, R> mapper) {
        
        List<R> mappedContent = page.getContent().stream()
                .map(mapper)
                .toList();
        
        return PagedResponse.<R>builder()
                .content(mappedContent)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .success(true)
                .message("Data retrieved successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }
}

