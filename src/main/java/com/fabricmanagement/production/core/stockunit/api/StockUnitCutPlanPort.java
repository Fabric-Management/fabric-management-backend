package com.fabricmanagement.production.core.stockunit.api;

import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos;
import java.util.List;
import java.util.UUID;

/** Commands join the commercial transaction and use its already verified provisional proof. */
public interface StockUnitCutPlanPort {
  StockUnitCutPlanDtos.View evaluate(
      StockUnitCutTargetPort.Context target,
      List<UUID> pieces,
      UUID cutSource,
      UUID session,
      UUID actor);

  StockUnitCutPlanDtos.View apply(
      StockUnitCutTargetPort.Context target,
      List<UUID> pieces,
      UUID cutSource,
      UUID session,
      UUID operation,
      UUID actor,
      String requestHash);

  StockUnitCutPlanDtos.View replay(UUID operation, UUID actor, UUID line, String requestHash);

  StockUnitCutPlanDtos.View evaluateDraft(DraftInput input, UUID actor);

  record DraftInput(
      UUID formId,
      UUID clientLineId,
      UUID productId,
      java.math.BigDecimal quantity,
      String unit,
      UUID ownLineId,
      UUID sessionId,
      List<UUID> sources,
      UUID cutSourceId) {}

  List<StockUnitCutPlan.Source> confirmationSources(UUID lineId, String targetFingerprint);

  boolean hasUnfinishedPlan(UUID lineId);

  boolean exactDeliveryReady(UUID lineId, java.math.BigDecimal quantity);

  void commercialLineChanged(UUID lineId);

  void transfer(UUID oldLineId, StockUnitCutTargetPort.Context target);

  java.util.Optional<Summary> currentForLine(UUID lineId);

  @io.swagger.v3.oas.annotations.media.Schema(name = "StockUnitCutPlanSummary")
  record Summary(
      UUID planId,
      com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlanState state,
      java.math.BigDecimal sourceQuantity,
      java.math.BigDecimal deliveryQuantity,
      java.math.BigDecimal remainderQuantity,
      String routesTo,
      @io.swagger.v3.oas.annotations.media.Schema(
              requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
          boolean physicalWorkPending) {}
}
