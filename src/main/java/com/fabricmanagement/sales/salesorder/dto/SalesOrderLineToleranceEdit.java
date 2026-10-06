package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** A line's agreed quantity tolerance as one key. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineToleranceEdit",
    description = "A line's agreed quantity tolerance as one key.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderLineToleranceEdit extends SalesOrderFieldEdit {

  private SalesOrderLineToleranceValue value;

  public static SalesOrderLineToleranceEdit set(SalesOrderLineToleranceValue value) {
    SalesOrderLineToleranceEdit edit = new SalesOrderLineToleranceEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderLineToleranceEdit clear() {
    SalesOrderLineToleranceEdit edit = new SalesOrderLineToleranceEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Valid
  @Schema(description = "Both limits with SET; CLEAR removes the tolerance")
  public SalesOrderLineToleranceValue getValue() {
    return value;
  }

  public void setValue(SalesOrderLineToleranceValue value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
