package com.fabricmanagement.sales.salesorder.domain.port;

import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DraftProductionOrderCommand(
    UUID recipeId,
    UUID tradingPartnerId,
    UUID salesOrderLineId,
    BigDecimal plannedQty,
    String unit,
    String currency,
    LocalDate deadline,
    String certificationReq,
    String originReq,
    UUID salesOrderId,
    UUID outputProductId,
    UUID requirementProfileId,
    Integer requirementProfileVersion,
    RequirementProfileSnapshot requirementProfileSnapshot,
    String productCode) {
  public DraftProductionOrderCommand(
      UUID recipeId,
      UUID tradingPartnerId,
      UUID salesOrderLineId,
      BigDecimal plannedQty,
      String unit,
      String currency,
      LocalDate deadline,
      String certificationReq,
      String originReq) {
    this(
        recipeId,
        tradingPartnerId,
        salesOrderLineId,
        plannedQty,
        unit,
        currency,
        deadline,
        certificationReq,
        originReq,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
