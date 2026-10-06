package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One line operation of a safe-edit save (CEDIT-02 §4.3). {@code lineId} names an existing line
 * (UPDATE, REMOVE); {@code clientLineId} and {@code productId} belong to ADD only. For these three
 * an absent and a null value are the same; which are required or forbidden is checked with 422.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "SalesOrderLineEdit", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderLineEdit {

  private SalesOrderLineEditOperation operation;
  private UUID lineId;
  private UUID clientLineId;
  private UUID productId;
  private SalesOrderLineFieldEdits fields;

  @NotNull(message = "Choose ADD, UPDATE or REMOVE")
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  public SalesOrderLineEditOperation getOperation() {
    return operation;
  }

  public void setOperation(SalesOrderLineEditOperation operation) {
    if (operation == null) {
      throw new IllegalArgumentException("A line operation must not be null");
    }
    this.operation = operation;
  }

  @Schema(description = "The existing line (UPDATE, REMOVE); never with ADD")
  public UUID getLineId() {
    return lineId;
  }

  public void setLineId(UUID lineId) {
    this.lineId = lineId;
  }

  @Schema(description = "The client's stable id of a new line (ADD only)")
  public UUID getClientLineId() {
    return clientLineId;
  }

  public void setClientLineId(UUID clientLineId) {
    this.clientLineId = clientLineId;
  }

  @Schema(description = "The product of a new line (ADD only); a line's product never changes here")
  public UUID getProductId() {
    return productId;
  }

  public void setProductId(UUID productId) {
    this.productId = productId;
  }

  @Valid
  @Schema(
      description = "ADD: SET instructions, quantity required. UPDATE: at least one. REMOVE: none")
  public SalesOrderLineFieldEdits getFields() {
    return fields;
  }

  public void setFields(SalesOrderLineFieldEdits fields) {
    if (fields == null) {
      throw new IllegalArgumentException("fields must not be null; omit it instead");
    }
    this.fields = fields;
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderLineEdit property: " + name);
  }
}
