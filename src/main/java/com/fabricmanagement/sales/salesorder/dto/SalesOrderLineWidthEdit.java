package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** A line's finished width with its unit, as one key. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineWidthEdit",
    description = "A line's finished width with its unit, as one key.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderLineWidthEdit extends SalesOrderFieldEdit {

  private SalesOrderLineWidthValue value;

  public static SalesOrderLineWidthEdit set(SalesOrderLineWidthValue value) {
    SalesOrderLineWidthEdit edit = new SalesOrderLineWidthEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderLineWidthEdit clear() {
    SalesOrderLineWidthEdit edit = new SalesOrderLineWidthEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Valid
  @Schema(description = "Width and unit with SET; CLEAR removes the width")
  public SalesOrderLineWidthValue getValue() {
    return value;
  }

  public void setValue(SalesOrderLineWidthValue value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
