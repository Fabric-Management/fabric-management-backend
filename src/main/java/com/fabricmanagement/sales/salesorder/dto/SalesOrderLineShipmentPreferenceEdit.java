package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** The line's shipment preference instruction; it always has a value, so CLEAR is refused. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineShipmentPreferenceEdit",
    description = "The line's shipment preference; CLEAR is refused.",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderLineShipmentPreferenceEdit extends SalesOrderFieldEdit {

  private LineShipmentPreference value;

  public static SalesOrderLineShipmentPreferenceEdit set(LineShipmentPreference value) {
    SalesOrderLineShipmentPreferenceEdit edit = new SalesOrderLineShipmentPreferenceEdit();
    edit.setOperation(SalesOrderFieldEditOperation.SET);
    edit.setValue(value);
    return edit;
  }

  public static SalesOrderLineShipmentPreferenceEdit clear() {
    SalesOrderLineShipmentPreferenceEdit edit = new SalesOrderLineShipmentPreferenceEdit();
    edit.setOperation(SalesOrderFieldEditOperation.CLEAR);
    return edit;
  }

  @Schema(description = "The new value with SET; never sent with CLEAR")
  public LineShipmentPreference getValue() {
    return value;
  }

  public void setValue(LineShipmentPreference value) {
    this.value = value;
    markValueGiven();
  }

  @Override
  public Object givenValue() {
    return value;
  }
}
