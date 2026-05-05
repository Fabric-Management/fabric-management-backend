package com.fabricmanagement.procurement.purchaseorder.dto;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePurchaseOrderRequest {

  @NotNull(message = "Work order ID is required")
  private UUID workOrderId;

  @NotNull(message = "Trading partner ID is required")
  private UUID tradingPartnerId;

  private UUID supplierQuoteId;

  @NotNull(message = "Currency is required")
  private String currency;

  private String paymentTerms;
  private LocalDate expectedDelivery;
  private String notes;

  /** Üretim modülü tipi. RFQ akışında otomatik set edilir, direkt PO'da frontend gönderir. */
  private PurchaseOrderModuleType moduleType;

  /** Modüle özgü spec'ler (JSONB olarak saklanacak). */
  private PurchaseOrderSpecs moduleSpecs;

  @NotEmpty(message = "At least one order line is required")
  @Valid
  private List<PurchaseOrderLineRequest> lines;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PurchaseOrderLineRequest {

    private UUID rfqLineId;
    private UUID materialId;
    private String productDesc;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
    private BigDecimal qty;

    @NotNull(message = "Unit is required")
    private String unit;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.0001", message = "Unit price must be greater than zero")
    private BigDecimal unitPrice;

    @NotNull(message = "Currency is required")
    private String currency;

    /** Line-level module-specific data. */
    private String moduleSpecs;
  }
}
