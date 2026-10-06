package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A reference field instruction (for example the colour card). */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderIdFieldEdit",
    description = "A reference field instruction (for example the colour card).",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderIdFieldEdit extends SalesOrderFieldEdit {

  private UUID value;

  public static SalesOrderIdFieldEdit set(UUID value) {
    SalesOrderIdFieldEdit edit = new SalesOrderIdFieldEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderIdFieldEdit clear() {
    SalesOrderIdFieldEdit edit = new SalesOrderIdFieldEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Schema(description = "The new reference with SET; never sent with CLEAR")
  public UUID getValue() {
    return value;
  }

  public void setValue(UUID value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
