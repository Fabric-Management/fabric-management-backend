package com.fabricmanagement.common.infrastructure.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

/**
 * Paginated response wrapper for list endpoints.
 *
 * <h2>Response Example:</h2>
 *
 * <pre>{@code
 * {
 *   "content": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 150,
 *   "totalPages": 8,
 *   "first": true,
 *   "last": false
 * }
 * }</pre>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * @GetMapping
 * public ResponseEntity<PagedResponse<ProductDto>> getAll(Pageable pageable) {
 *     Page<Product> page = productRepository.findAll(pageable);
 *     return ResponseEntity.ok(PagedResponse.from(page, ProductDto::from));
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private List<T> content;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int page;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int size;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private long totalElements;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int totalPages;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean first;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean last;

  public static <T, E> PagedResponse<T> from(
      Page<E> page, java.util.function.Function<E, T> mapper) {
    return PagedResponse.<T>builder()
        .content(page.getContent().stream().map(mapper).toList())
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .first(page.isFirst())
        .last(page.isLast())
        .build();
  }

  public static <T> PagedResponse<T> from(Page<T> page) {
    return PagedResponse.<T>builder()
        .content(page.getContent())
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .first(page.isFirst())
        .last(page.isLast())
        .build();
  }
}
