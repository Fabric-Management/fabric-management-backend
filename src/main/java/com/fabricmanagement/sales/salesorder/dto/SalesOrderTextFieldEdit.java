package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A text field instruction; SET never takes a blank text, CLEAR is the only way to empty a field.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderTextFieldEdit",
    description =
        "A text field instruction; SET never takes a blank text, CLEAR is the only way to empty a field.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderTextFieldEdit extends SalesOrderFieldEdit {

  private String value;

  public static SalesOrderTextFieldEdit set(String value) {
    SalesOrderTextFieldEdit edit = new SalesOrderTextFieldEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderTextFieldEdit clear() {
    SalesOrderTextFieldEdit edit = new SalesOrderTextFieldEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Schema(description = "The new text with SET; never sent with CLEAR")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
