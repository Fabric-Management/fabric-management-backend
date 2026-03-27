package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * WIP limiti asildiginda firlatilir -- SELF assign icin sert, Manager icin soft uyari.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} -- WIP Limiti
 */
public class WipLimitExceededException extends DomainException {

  public WipLimitExceededException(String message) {
    super(message, "WIP_LIMIT_EXCEEDED", 409);
  }
}
