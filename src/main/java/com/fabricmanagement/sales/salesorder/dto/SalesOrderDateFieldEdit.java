package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** A date field instruction. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderDateFieldEdit",
    description = "A date field instruction.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderDateFieldEdit extends SalesOrderFieldEdit {

  private LocalDate value;

  public static SalesOrderDateFieldEdit set(LocalDate value) {
    SalesOrderDateFieldEdit edit = new SalesOrderDateFieldEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderDateFieldEdit clear() {
    SalesOrderDateFieldEdit edit = new SalesOrderDateFieldEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Schema(description = "The new date with SET; never sent with CLEAR")
  public LocalDate getValue() {
    return value;
  }

  public void setValue(LocalDate value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
