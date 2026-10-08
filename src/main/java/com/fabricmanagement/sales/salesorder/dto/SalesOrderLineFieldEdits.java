package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Line instructions of a safe-edit save (CEDIT-02 §2.3). There is no product here: a line's product
 * changes only through the product correction.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineFieldEdits",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderLineFieldEdits {

  private SalesOrderTextFieldEdit productDesc;
  private SalesOrderIdFieldEdit colorId;
  private SalesOrderLineWidthEdit finishedWidth;
  private SalesOrderDateFieldEdit requestedDeliveryDate;
  private SalesOrderFlagFieldEdit singleLotRequired;
  private SalesOrderLineShipmentPreferenceEdit shipmentPreference;
  private SalesOrderLineQuantityEdit quantity;
  private SalesOrderLinePricingEdit pricing;
  private SalesOrderLineToleranceEdit tolerance;
  private SalesOrderLineSpecificationEdit specification;

  @Valid
  @Schema(description = "Optional line note; never a substitute for the product")
  public SalesOrderTextFieldEdit getProductDesc() {
    return productDesc;
  }

  public void setProductDesc(SalesOrderTextFieldEdit productDesc) {
    this.productDesc = present("productDesc", productDesc);
  }

  @Valid
  @Schema(description = "Colour card of the distribution")
  public SalesOrderIdFieldEdit getColorId() {
    return colorId;
  }

  public void setColorId(SalesOrderIdFieldEdit colorId) {
    this.colorId = present("colorId", colorId);
  }

  @Valid
  @Schema(description = "Finished width with its unit, as one key")
  public SalesOrderLineWidthEdit getFinishedWidth() {
    return finishedWidth;
  }

  public void setFinishedWidth(SalesOrderLineWidthEdit finishedWidth) {
    this.finishedWidth = present("finishedWidth", finishedWidth);
  }

  @Valid
  @Schema(description = "Customer-requested delivery date of this distribution")
  public SalesOrderDateFieldEdit getRequestedDeliveryDate() {
    return requestedDeliveryDate;
  }

  public void setRequestedDeliveryDate(SalesOrderDateFieldEdit requestedDeliveryDate) {
    this.requestedDeliveryDate = present("requestedDeliveryDate", requestedDeliveryDate);
  }

  @Valid
  @Schema(description = "Whether the distribution must come from one dye lot; CLEAR is refused")
  public SalesOrderFlagFieldEdit getSingleLotRequired() {
    return singleLotRequired;
  }

  public void setSingleLotRequired(SalesOrderFlagFieldEdit singleLotRequired) {
    this.singleLotRequired = present("singleLotRequired", singleLotRequired);
  }

  @Valid
  @Schema(
      description =
          "How the distribution may ship once ready; omitted on ADD means AS_READY, CLEAR is"
              + " refused")
  public SalesOrderLineShipmentPreferenceEdit getShipmentPreference() {
    return shipmentPreference;
  }

  public void setShipmentPreference(SalesOrderLineShipmentPreferenceEdit shipmentPreference) {
    this.shipmentPreference = present("shipmentPreference", shipmentPreference);
  }

  @Valid
  @Schema(description = "Requested quantity in its unit; required on ADD, CLEAR is refused")
  public SalesOrderLineQuantityEdit getQuantity() {
    return quantity;
  }

  public void setQuantity(SalesOrderLineQuantityEdit quantity) {
    this.quantity = present("quantity", quantity);
  }

  @Valid
  @Schema(description = "Price in its currency with discount and tax, as one key")
  public SalesOrderLinePricingEdit getPricing() {
    return pricing;
  }

  public void setPricing(SalesOrderLinePricingEdit pricing) {
    this.pricing = present("pricing", pricing);
  }

  @Valid
  @Schema(description = "Agreed quantity tolerance, as one key")
  public SalesOrderLineToleranceEdit getTolerance() {
    return tolerance;
  }

  public void setTolerance(SalesOrderLineToleranceEdit tolerance) {
    this.tolerance = present("tolerance", tolerance);
  }

  @Valid
  @Schema(
      description =
          "Module type, module specs and requirement profile, as one key; CLEAR is refused")
  public SalesOrderLineSpecificationEdit getSpecification() {
    return specification;
  }

  public void setSpecification(SalesOrderLineSpecificationEdit specification) {
    this.specification = present("specification", specification);
  }

  /** The instructions the request carried, by edit-key name, in a fixed order. */
  public Map<String, SalesOrderFieldEdit> instructions() {
    Map<String, SalesOrderFieldEdit> instructions = new LinkedHashMap<>();
    put(instructions, "line.productDesc", productDesc);
    put(instructions, "line.colorId", colorId);
    put(instructions, "line.finishedWidth", finishedWidth);
    put(instructions, "line.requestedDeliveryDate", requestedDeliveryDate);
    put(instructions, "line.singleLotRequired", singleLotRequired);
    put(instructions, "line.shipmentPreference", shipmentPreference);
    put(instructions, "line.quantity", quantity);
    put(instructions, "line.pricing", pricing);
    put(instructions, "line.tolerance", tolerance);
    put(instructions, "line.specification", specification);
    return Collections.unmodifiableMap(instructions);
  }

  private static void put(
      Map<String, SalesOrderFieldEdit> instructions, String key, SalesOrderFieldEdit edit) {
    if (edit != null) {
      instructions.put(key, edit);
    }
  }

  /** An explicit null is not "no instruction": it is refused like any unreadable body. */
  private static <T> T present(String name, T value) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null; omit it to leave it unchanged");
    }
    return value;
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderLineFieldEdits property: " + name);
  }
}
