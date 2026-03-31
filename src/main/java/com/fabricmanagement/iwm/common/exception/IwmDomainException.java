package com.fabricmanagement.iwm.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * IWM modülüne özel domain exception. Tüm IWM alt-modüllerinde (reservation, rules, stockcount,
 * transfer, rma, adjustment) domain ihlallerinde fırlatılır.
 */
public class IwmDomainException extends DomainException {

  public IwmDomainException(String message) {
    super(message, "IWM_RULE_VIOLATION", 400);
  }

  public IwmDomainException(String message, Throwable cause) {
    super(message, "IWM_RULE_VIOLATION", 400, cause);
  }
}
