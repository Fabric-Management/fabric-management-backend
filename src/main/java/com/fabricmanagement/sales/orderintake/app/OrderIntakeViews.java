package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Small read views returned by order-intake commands. */
public final class OrderIntakeViews {

  private OrderIntakeViews() {}

  @Schema(name = "AgreedQuantityTolerance")
  public record AgreedTolerance(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal upPct,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal downPct,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String source,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID recordedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant recordedAt) {

    public static AgreedTolerance from(SalesOrder order) {
      return new AgreedTolerance(
          order.getId(),
          order.getAgreedToleranceUpPct(),
          order.getAgreedToleranceDownPct(),
          order.getAgreedToleranceSource(),
          order.getAgreedToleranceRecordedBy(),
          order.getAgreedToleranceRecordedAt());
    }
  }
}
