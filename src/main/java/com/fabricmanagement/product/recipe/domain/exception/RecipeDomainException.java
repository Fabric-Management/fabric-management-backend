package com.fabricmanagement.product.recipe.domain.exception;

import com.fabricmanagement.product.common.exception.ProductDomainException;

/**
 * Base exception for recipe (BOM / fiber formula) domain rule violations.
 *
 * <p>Throw this (or a subclass) for any business rule violation specific to product recipes.
 * Examples:
 *
 * <ul>
 *   <li>Recipe references a deactivated fiber or product
 *   <li>Total composition weight or percentage is out of valid range
 *   <li>Recipe version conflict (concurrent edits)
 *   <li>A recipe used in an active production order cannot be modified
 * </ul>
 */
public class RecipeDomainException extends ProductDomainException {

  public RecipeDomainException(String message) {
    super(message);
  }

  public RecipeDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
