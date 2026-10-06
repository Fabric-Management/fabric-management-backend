package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** A line's module type, module specs and requirement profile as one key; it cannot be cleared. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineSpecificationEdit",
    description =
        "A line's module type, module specs and requirement profile as one key; it cannot be cleared.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderLineSpecificationEdit extends SalesOrderFieldEdit {

  private SalesOrderLineSpecificationValue value;

  public static SalesOrderLineSpecificationEdit set(SalesOrderLineSpecificationValue value) {
    SalesOrderLineSpecificationEdit edit = new SalesOrderLineSpecificationEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderLineSpecificationEdit clear() {
    SalesOrderLineSpecificationEdit edit = new SalesOrderLineSpecificationEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Valid
  @Schema(description = "The whole specification with SET")
  public SalesOrderLineSpecificationValue getValue() {
    return value;
  }

  public void setValue(SalesOrderLineSpecificationValue value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
