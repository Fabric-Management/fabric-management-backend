package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** A line's agreed price in its currency with discount and tax, as one key. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLinePricingEdit",
    description = "A line's agreed price in its currency with discount and tax, as one key.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderLinePricingEdit extends SalesOrderFieldEdit {

  private SalesOrderLinePricingValue value;

  public static SalesOrderLinePricingEdit set(SalesOrderLinePricingValue value) {
    SalesOrderLinePricingEdit edit = new SalesOrderLinePricingEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderLinePricingEdit clear() {
    SalesOrderLinePricingEdit edit = new SalesOrderLinePricingEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Valid
  @Schema(description = "The whole pricing with SET; CLEAR leaves the line unpriced")
  public SalesOrderLinePricingValue getValue() {
    return value;
  }

  public void setValue(SalesOrderLinePricingValue value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
