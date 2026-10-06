package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** The delivery term as one key: rule, named place, edition, standing and contract reference. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderDeliveryTermsEdit",
    description =
        "The delivery term as one key: rule, named place, edition, standing and contract reference.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderDeliveryTermsEdit extends SalesOrderFieldEdit {

  private SalesOrderDeliveryTermsValue value;

  public static SalesOrderDeliveryTermsEdit set(SalesOrderDeliveryTermsValue value) {
    SalesOrderDeliveryTermsEdit edit = new SalesOrderDeliveryTermsEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderDeliveryTermsEdit clear() {
    SalesOrderDeliveryTermsEdit edit = new SalesOrderDeliveryTermsEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Valid
  @Schema(description = "The whole delivery term with SET; CLEAR removes the term")
  public SalesOrderDeliveryTermsValue getValue() {
    return value;
  }

  public void setValue(SalesOrderDeliveryTermsValue value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
