package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.FulfillmentMode;
import com.fabricmanagement.sales.quote.domain.QuoteLineDeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class UpdateQuoteLineRequest {

  private UUID qualityGradeId;

  private UUID colorId;

  @Valid private List<QuoteLineLotSelectionRequest> selectedLots;

  private QuoteLineDeliveryStatus deliveryStatus;

  private LocalDate deliveryDate;

  @Schema(
      description = "Manual line-level fulfilment mode; omission or null clears it to pending",
      nullable = true)
  private FulfillmentMode fulfillmentMode;

  @NotNull(message = "Requested quantity is required")
  @DecimalMin(value = "0.001", message = "Requested quantity must be greater than zero")
  private BigDecimal requestedQty;

  @NotBlank(message = "Unit is required")
  @Size(max = 20, message = "Unit must be 20 characters or less")
  private String unit;

  @NotNull(message = "Offered price is required")
  @DecimalMin(value = "0.0001", message = "Offered price must be greater than zero")
  private BigDecimal offeredPrice;
}
