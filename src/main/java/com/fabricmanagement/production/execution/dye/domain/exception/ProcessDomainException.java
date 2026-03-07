package com.fabricmanagement.production.execution.dye.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

/**
 * Base exception for dye &amp; finishing process domain rule violations.
 *
 * <p>This class is the root for all exceptions thrown during downstream textile processes — dyeing,
 * finishing, coating, and related chemical treatments applied to fabric rolls.
 *
 * <p>Subclasses to be created as the process module is built:
 *
 * <ul>
 *   <li>{@code DyeOrderDomainException} — lifecycle violations for dye orders
 *   <li>{@code ColourFormulaDomainException} — colour recipe adherence violations
 *   <li>{@code FinishingProcessException} — constraint violations for finishing operations
 * </ul>
 *
 * <p>TODO(module): Dye &amp; finishing execution module is stubbed. This exception class is a
 * placeholder for when {@code production/execution/dye/} is implemented.
 */
public class ProcessDomainException extends ProductionDomainException {

  public ProcessDomainException(String message) {
    super(message);
  }

  public ProcessDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
