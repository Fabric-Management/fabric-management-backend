package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.sales.orderintake.domain.PartialDeliveryPreference;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What stands between a draft and the customer's approval, and what the current user may do about
 * it (SOI D4, IK-11). An order is confirmed only by the customer's approval of the sent version.
 * The frontend shows this as is; it derives neither rules nor permissions.
 */
@Schema(name = "OrderIntakeReadiness")
public record OrderIntakeReadinessDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String orderStatus,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartialDeliveryPreference partialDelivery,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Block> orderBlocks,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Line> lines,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Capability> capabilities,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant evaluatedAt) {

  @Schema(name = "OrderIntakeReadinessBlock")
  public record Block(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> pieceIds) {}

  @Schema(name = "OrderIntakeReadinessLine")
  public record Line(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderLineId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Null: no stock choice; the line is covered from other sources")
          QuantityAcceptanceBasis stockChoice,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean conditional,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          BigDecimal acceptedStockQuantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int allocatedPieces,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          BigDecimal allocatedQuantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String allocatedUnit,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Block> blocks) {}

  @Schema(name = "OrderIntakeCapability")
  public record Capability(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderIntakeAction action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String reason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          List<PermissionKey> requiredPermissions) {}
}
