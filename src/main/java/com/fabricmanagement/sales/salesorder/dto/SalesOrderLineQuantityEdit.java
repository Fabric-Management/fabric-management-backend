package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** A line's requested quantity in its unit, as one key; it cannot be cleared. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineQuantityEdit",
    description = "A line's requested quantity in its unit, as one key; it cannot be cleared.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderLineQuantityEdit extends SalesOrderFieldEdit {

  private SalesOrderLineQuantityValue value;

  public static SalesOrderLineQuantityEdit set(SalesOrderLineQuantityValue value) {
    SalesOrderLineQuantityEdit edit = new SalesOrderLineQuantityEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderLineQuantityEdit clear() {
    SalesOrderLineQuantityEdit edit = new SalesOrderLineQuantityEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Valid
  @Schema(description = "The quantity and unit with SET")
  public SalesOrderLineQuantityValue getValue() {
    return value;
  }

  public void setValue(SalesOrderLineQuantityValue value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
