package com.fabricmanagement.iwm.common.exception;

/**
 * IWM modülüne özel domain exception. Tüm IWM alt-modüllerinde (reservation, rules, stockcount,
 * transfer, rma, adjustment) domain ihlallerinde fırlatılır.
 */
public class IwmDomainException extends RuntimeException {

  public IwmDomainException(String message) {
    super(message);
  }

  public IwmDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
