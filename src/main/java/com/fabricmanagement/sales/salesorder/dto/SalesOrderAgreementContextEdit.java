package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** Where the order was agreed, with its description, as one key. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderAgreementContextEdit",
    description = "Where the order was agreed, with its description, as one key.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderAgreementContextEdit extends SalesOrderFieldEdit {

  private SalesOrderAgreementContextValue value;

  public static SalesOrderAgreementContextEdit set(SalesOrderAgreementContextValue value) {
    SalesOrderAgreementContextEdit edit = new SalesOrderAgreementContextEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderAgreementContextEdit clear() {
    SalesOrderAgreementContextEdit edit = new SalesOrderAgreementContextEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Valid
  @Schema(description = "The whole context with SET")
  public SalesOrderAgreementContextValue getValue() {
    return value;
  }

  public void setValue(SalesOrderAgreementContextValue value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
