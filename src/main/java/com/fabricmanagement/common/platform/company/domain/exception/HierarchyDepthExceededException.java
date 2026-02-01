package com.fabricmanagement.common.platform.company.domain.exception;

/**
 * Thrown when company hierarchy depth would exceed the maximum allowed.
 *
 * <p>Used by {@link com.fabricmanagement.common.platform.company.app.CompanyHierarchyService}.
 */
public class HierarchyDepthExceededException extends RuntimeException {

  public HierarchyDepthExceededException(String message) {
    super(message);
  }
}
