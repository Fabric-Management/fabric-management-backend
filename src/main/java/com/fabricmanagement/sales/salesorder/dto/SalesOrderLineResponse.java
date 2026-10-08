package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SalesOrderLineResponse {

  UUID id;
  String uid;
  UUID salesOrderId;
  UUID productId;
  String productDesc;
  UUID colorId;
  BigDecimal finishedWidth;
  String finishedWidthUnit;
  java.time.LocalDate requestedDeliveryDate;

  @Schema(description = "Quantity first requested by the customer (immutable)")
  BigDecimal initialRequestedQty;

  boolean singleLotRequired;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "How the distribution may ship once ready (LINE-PREFERENCES-1)")
  LineShipmentPreference shipmentPreference;

  BigDecimal requestedQty;
  BigDecimal shippedQty;
  String unit;
  BigDecimal unitPrice;
  String currency;

  @Schema(description = "Discount on this line in the line currency")
  BigDecimal discountAmount;

  @Schema(description = "Tax on this line in the line currency")
  BigDecimal taxAmount;

  @Schema(description = "Agreed quantity tolerance above the requested quantity (%)")
  BigDecimal toleranceUpPct;

  @Schema(description = "Agreed quantity tolerance below the requested quantity (%)")
  BigDecimal toleranceDownPct;

  UUID toleranceRecordedBy;
  java.time.Instant toleranceRecordedAt;
  ModuleType moduleType;

  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  Map<String, Object> moduleSpecs;

  RequirementProfileSnapshot requirementProfile;

  SalesOrderLineStatus lineStatus;
  UUID recipeId;

  @Schema(description = "Line version; commands that change the line send it back (SOI R19)")
  Long version;
}
