package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** The customer's contact person as one key: name, e-mail, phone and WhatsApp consent. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderContactEdit",
    description =
        "The customer's contact person as one key: name, e-mail, phone and WhatsApp consent.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderContactEdit extends SalesOrderFieldEdit {

  private SalesOrderContactValue value;

  public static SalesOrderContactEdit set(SalesOrderContactValue value) {
    SalesOrderContactEdit edit = new SalesOrderContactEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderContactEdit clear() {
    SalesOrderContactEdit edit = new SalesOrderContactEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Valid
  @Schema(description = "The whole contact with SET; CLEAR empties all of it")
  public SalesOrderContactValue getValue() {
    return value;
  }

  public void setValue(SalesOrderContactValue value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
