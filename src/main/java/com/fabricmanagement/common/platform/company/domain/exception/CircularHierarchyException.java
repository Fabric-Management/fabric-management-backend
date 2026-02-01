package com.fabricmanagement.common.platform.company.domain.exception;

/**
 * Thrown when a parent assignment would create a circular reference in company hierarchy.
 *
 * <p>Used by {@link com.fabricmanagement.common.platform.company.app.CompanyHierarchyService}.
 */
public class CircularHierarchyException extends RuntimeException {

  public CircularHierarchyException(String message) {
    super(message);
  }
}
