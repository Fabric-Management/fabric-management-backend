package com.fabricmanagement.common.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Framework-agnostic pagination request DTO for list endpoints. Maps query parameters like
 * ?page=0&size=20&sortBy=id&sortDirection=DESC
 */
@Data
public class PageRequestDto {

  @Min(value = 0, message = "Page index must not be less than zero")
  private int page = 0;

  @Min(value = 1, message = "Page size must not be less than one")
  @Max(value = 100, message = "Page size must not be greater than 100")
  private int size = 20;

  private String sortBy;

  @Pattern(regexp = "^(ASC|DESC|asc|desc)$", message = "Sort direction must be ASC or DESC")
  private String sortDirection = "ASC";

  /**
   * Converts this DTO to a Spring Data Pageable object.
   *
   * @return Pageable object
   */
  public Pageable toPageable() {
    if (sortBy != null && !sortBy.trim().isEmpty()) {
      Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
      return PageRequest.of(page, size, sort);
    }
    return PageRequest.of(page, size);
  }

  /**
   * Converts this DTO to a Spring Data Pageable object with a default sort.
   *
   * @param defaultSort Default sorting if not provided in the request
   * @return Pageable object
   */
  public Pageable toPageable(Sort defaultSort) {
    if (sortBy != null && !sortBy.trim().isEmpty()) {
      Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
      return PageRequest.of(page, size, sort);
    }
    if (defaultSort != null) {
      return PageRequest.of(page, size, defaultSort);
    }
    return PageRequest.of(page, size);
  }
}
