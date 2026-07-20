package com.fabricmanagement.production.execution.goodsreceipt.dto;

import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

  @Schema(
      description = "Required PO line ID when sourceType is PURCHASE_ORDER; ignored otherwise",
      nullable = true)
  private UUID sourceLineId;

  @Size(max = 100)
  @Schema(description = "Optional supplier lot or batch reference", maxLength = 100)
  private String supplierBatchCode;

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

    @Positive(message = "Length must be positive when provided")
    @Schema(description = "Physical item length; required for FABRIC purchase receipts")
    private BigDecimal length;

    @Size(max = 10)
    @Schema(description = "Metric length unit: M, CM, or MM", maxLength = 10)
    private String lengthUnit;

    private String serialNumber;
    private String notes;
  }
}
