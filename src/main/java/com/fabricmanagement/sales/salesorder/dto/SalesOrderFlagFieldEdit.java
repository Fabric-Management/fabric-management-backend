package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** A yes/no field instruction; false is a value, not a clear. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderFlagFieldEdit",
    description = "A yes/no field instruction; false is a value, not a clear.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderFlagFieldEdit extends SalesOrderFieldEdit {

  private Boolean value;

  public static SalesOrderFlagFieldEdit set(Boolean value) {
    SalesOrderFlagFieldEdit edit = new SalesOrderFlagFieldEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderFlagFieldEdit clear() {
    SalesOrderFlagFieldEdit edit = new SalesOrderFlagFieldEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Schema(description = "The new value with SET; never sent with CLEAR")
  public Boolean getValue() {
    return value;
  }

  public void setValue(Boolean value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
