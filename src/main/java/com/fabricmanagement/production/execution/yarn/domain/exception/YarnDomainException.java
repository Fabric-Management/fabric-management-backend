package com.fabricmanagement.production.execution.yarn.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

/**
 * Base exception for yarn production domain rule violations.
 *
 * <p>This class is the root for all exceptions thrown during yarn manufacturing execution — from
 * the moment fiber batches are fed into the spinning process through to yarn batch completion.
 *
 * <p>Subclasses to be created as the yarn module is built:
 *
 * <ul>
 *   <li>{@code YarnBatchDomainException} — lifecycle violations for yarn lots
 *   <li>{@code YarnCompositionException} — recipe adherence violations during spinning
 *   <li>{@code SpinningProcessException} — machine/workcenter constraint violations
 * </ul>
 *
 * <p>TODO(module): Yarn execution module is stubbed. This exception class is a placeholder for when
 * {@code production/execution/yarn/} is implemented.
 */
public class YarnDomainException extends ProductionDomainException {

  public YarnDomainException(String message) {
    super(message);
  }

  public YarnDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
