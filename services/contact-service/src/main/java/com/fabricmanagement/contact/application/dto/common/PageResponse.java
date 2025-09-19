package com.fabricmanagement.contact.application.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic page response wrapper.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean hasNext;
    private boolean hasPrevious;
    
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page, PageRequest pageRequest) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    public static <T, R> PageResponse<R> of(org.springframework.data.domain.Page<T> page, java.util.function.Function<T, R> mapper, PageRequest pageRequest) {
        return PageResponse.<R>builder()
            .content(page.getContent().stream().map(mapper).collect(java.util.stream.Collectors.toList()))
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    public static <T, R> PageResponse<R> of(org.springframework.data.domain.Page<T> page, java.util.function.Function<T, R> mapper) {
        return PageResponse.<R>builder()
            .content(page.getContent().stream().map(mapper).collect(java.util.stream.Collectors.toList()))
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    public static <T, R> PageResponse<R> of(org.springframework.data.domain.Page<T> page, java.util.function.Function<T, R> mapper, PageRequest pageRequest, Class<R> responseType) {
        return PageResponse.<R>builder()
            .content(page.getContent().stream().map(mapper).collect(java.util.stream.Collectors.toList()))
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    public static <T, R> PageResponse<R> of(org.springframework.data.domain.Page<T> page, java.util.function.Function<T, R> mapper, PageRequest pageRequest, Class<R> responseType, String sortBy) {
        return PageResponse.<R>builder()
            .content(page.getContent().stream().map(mapper).collect(java.util.stream.Collectors.toList()))
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    public static <T, R> PageResponse<R> of(org.springframework.data.domain.Page<T> page, java.util.function.Function<T, R> mapper, PageRequest pageRequest, Class<R> responseType, String sortBy, String sortDirection) {
        return PageResponse.<R>builder()
            .content(page.getContent().stream().map(mapper).collect(java.util.stream.Collectors.toList()))
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    public static <T, R> PageResponse<R> of(org.springframework.data.domain.Page<T> page, java.util.function.Function<T, R> mapper, PageRequest pageRequest, Class<R> responseType, String sortBy, String sortDirection, String filter) {
        return PageResponse.<R>builder()
            .content(page.getContent().stream().map(mapper).collect(java.util.stream.Collectors.toList()))
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
    
    public static <T, R> PageResponse<R> of(org.springframework.data.domain.Page<T> page, java.util.function.Function<T, R> mapper, PageRequest pageRequest, Class<R> responseType, String sortBy, String sortDirection, String filter, String search) {
        return PageResponse.<R>builder()
            .content(page.getContent().stream().map(mapper).collect(java.util.stream.Collectors.toList()))
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
}
