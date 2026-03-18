package com.fabricmanagement.flowboard.task.app;

/**
 * WIP limiti aşıldığında fırlatılır — SELF assign için sert, Manager için soft uyarı.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — WIP Limiti
 */
public class WipLimitExceededException extends RuntimeException {

  public WipLimitExceededException(String message) {
    super(message);
  }
}
