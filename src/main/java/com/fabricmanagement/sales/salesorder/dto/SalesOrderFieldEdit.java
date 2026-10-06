package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * One field instruction of a safe-edit save (CEDIT-02 §4.2): {@code {"operation":"SET","value":…}}
 * or {@code {"operation":"CLEAR"}}. A property absent from its container is no instruction at all.
 * Setters record what the JSON carried, so an absent {@code value} and an explicit {@code null}
 * stay apart; that presence never reaches the wire or the OpenAPI document. Every concrete
 * instruction rejects unknown properties.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // CLEAR is written without a value
public abstract class SalesOrderFieldEdit {

  private SalesOrderFieldEditOperation operation;
  private boolean valueGiven;

  @NotNull(message = "Choose SET or CLEAR")
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  public SalesOrderFieldEditOperation getOperation() {
    return operation;
  }

  public void setOperation(SalesOrderFieldEditOperation operation) {
    if (operation == null) {
      throw new IllegalArgumentException("A field instruction's operation must not be null");
    }
    this.operation = operation;
  }

  /** True when the request carried the {@code value} property, an explicit null included. */
  public boolean valueGiven() {
    return valueGiven;
  }

  /** The value the request carried; null when it was absent or null. */
  public abstract Object givenValue();

  protected final void markValueGiven() {
    this.valueGiven = true;
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown field-instruction property: " + name);
  }
}
