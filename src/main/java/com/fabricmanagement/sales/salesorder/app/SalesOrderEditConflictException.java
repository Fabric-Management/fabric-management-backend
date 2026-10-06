package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A safe-edit save that saved nothing because of conflicts (CEDIT-02 §5.6). It is raised only after
 * the transaction that recorded the conflict's base and receipt committed, so nothing is rolled
 * back; it carries the exact problem body, which a repeat of the same save answers again.
 */
public class SalesOrderEditConflictException extends DomainException {

  private final transient JsonNode body;

  public SalesOrderEditConflictException(String code, JsonNode body) {
    super("The save conflicts with changes made since its base", code, 409);
    this.body = body;
  }

  /** The problem body as recorded on the save's receipt. */
  public JsonNode body() {
    return body;
  }
}
