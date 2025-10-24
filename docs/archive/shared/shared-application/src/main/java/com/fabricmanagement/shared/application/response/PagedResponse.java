package com.fabricmanagement.shared.application.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Paged Response
 * 
 * Standard paginated response wrapper
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ RESPONSE WRAPPER
 * ✅ PAGINATION SUPPORT
 */
@Getter
@Setter
@Builder
@ToString
public class PagedResponse<T> {
    
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;
    private String message;
    
    /**
     * Create PagedResponse from Spring Page
     */
    public static <T> PagedResponse<T> of(List<T> content, long totalElements, int totalPages, 
                                         int currentPage, int pageSize, boolean hasNext, boolean hasPrevious) {
        return PagedResponse.<T>builder()
            .content(content)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .currentPage(currentPage)
            .pageSize(pageSize)
            .hasNext(hasNext)
            .hasPrevious(hasPrevious)
            .build();
    }
    
    /**
     * Create PagedResponse with message
     */
    public static <T> PagedResponse<T> of(List<T> content, long totalElements, int totalPages, 
                                         int currentPage, int pageSize, boolean hasNext, boolean hasPrevious, 
                                         String message) {
        return PagedResponse.<T>builder()
            .content(content)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .currentPage(currentPage)
            .pageSize(pageSize)
            .hasNext(hasNext)
            .hasPrevious(hasPrevious)
            .message(message)
            .build();
    }
    
    /**
     * Create empty PagedResponse
     */
    public static <T> PagedResponse<T> empty() {
        return PagedResponse.<T>builder()
            .content(List.of())
            .totalElements(0)
            .totalPages(0)
            .currentPage(0)
            .pageSize(0)
            .hasNext(false)
            .hasPrevious(false)
            .build();
    }
    
    /**
     * Create empty PagedResponse with message
     */
    public static <T> PagedResponse<T> empty(String message) {
        return PagedResponse.<T>builder()
            .content(List.of())
            .totalElements(0)
            .totalPages(0)
            .currentPage(0)
            .pageSize(0)
            .hasNext(false)
            .hasPrevious(false)
            .message(message)
            .build();
    }
    
    /**
     * Get content size
     */
    public int getContentSize() {
        return content != null ? content.size() : 0;
    }
    
    /**
     * Check if response is empty
     */
    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }
    
    /**
     * Check if response has content
     */
    public boolean hasContent() {
        return !isEmpty();
    }
    
    /**
     * Get first element
     */
    public T getFirst() {
        return hasContent() ? content.get(0) : null;
    }
    
    /**
     * Get last element
     */
    public T getLast() {
        return hasContent() ? content.get(content.size() - 1) : null;
    }
    
    /**
     * Get element at index
     */
    public T get(int index) {
        if (content == null || index < 0 || index >= content.size()) {
            return null;
        }
        return content.get(index);
    }
    
    /**
     * Check if current page is first page
     */
    public boolean isFirstPage() {
        return currentPage == 0;
    }
    
    /**
     * Check if current page is last page
     */
    public boolean isLastPage() {
        return currentPage >= totalPages - 1;
    }
    
    /**
     * Get next page number
     */
    public int getNextPage() {
        return hasNext ? currentPage + 1 : currentPage;
    }
    
    /**
     * Get previous page number
     */
    public int getPreviousPage() {
        return hasPrevious ? currentPage - 1 : currentPage;
    }
    
    /**
     * Get page range info
     */
    public String getPageRange() {
        if (totalElements == 0) {
            return "0-0 of 0";
        }
        
        int start = currentPage * pageSize + 1;
        int end = Math.min(start + pageSize - 1, (int) totalElements);
        
        return String.format("%d-%d of %d", start, end, totalElements);
    }
    
    /**
     * Get pagination summary
     */
    public String getPaginationSummary() {
        return String.format("Page %d of %d (%s)", 
                           currentPage + 1, totalPages, getPageRange());
    }
}