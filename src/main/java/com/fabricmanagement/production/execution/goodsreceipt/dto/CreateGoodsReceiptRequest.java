package com.fabricmanagement.production.execution.goodsreceipt.dto;

import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for creating a new GoodsReceipt with its line items. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGoodsReceiptRequest {

  @NotNull(message = "Source type is required")
  private GoodsReceiptSourceType sourceType;

  @NotNull(message = "Source ID is required")
  private UUID sourceId;

  @NotNull(message = "Received by user ID is required")
  private UUID receivedById;

  /** Optional — defaults to confirmation time if not provided. */
  private Instant receivedAt;

  @NotNull(message = "Package count is required")
  @Min(value = 1, message = "Package count must be at least 1")
  private Integer packageCount;

  private BigDecimal grossWeight;
  private BigDecimal netWeight;
  private String vehicleInfo;
  private String damageNotes;

  @NotEmpty(message = "At least one item is required")
  @Valid
  private List<GoodsReceiptItemRequest> items;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GoodsReceiptItemRequest {

    @NotNull(message = "Net weight is required for each item")
    private BigDecimal netWeight;

    private BigDecimal grossWeight;
    private String serialNumber;
    private String notes;
  }
}
