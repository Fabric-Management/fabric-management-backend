package com.fabricmanagement.production.masterdata.recipe.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

/**
 * Base exception for recipe (BOM / fiber formula) domain rule violations.
 *
 * <p>Throw this (or a subclass) for any business rule violation specific to production recipes.
 * Examples:
 *
 * <ul>
 *   <li>Recipe references a deactivated fiber or product
 *   <li>Total composition weight or percentage is out of valid range
 *   <li>Recipe version conflict (concurrent edits)
 *   <li>A recipe used in an active production order cannot be modified
 * </ul>
 */
public class RecipeDomainException extends ProductionDomainException {

  public RecipeDomainException(String message) {
    super(message);
  }

  public RecipeDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
