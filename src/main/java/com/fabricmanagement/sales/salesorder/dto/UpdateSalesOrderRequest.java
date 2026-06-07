package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class UpdateSalesOrderRequest {

  @NotNull(message = "Version is required for optimistic locking")
  private Long version;

  private String customerReference;

  @NotNull(message = "Order date is required")
  private LocalDate orderDate;

  private LocalDate requestedDeliveryDate;
  private LocalDate promisedDeliveryDate;

  private BigDecimal taxAmount;
  private BigDecimal discountAmount;

  @NotBlank(message = "Currency is required")
  @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO code")
  private String currency;

  private String shippingAddress;
  private String billingAddress;
  private String shippingMethod;
  private String notes;
  private Map<String, Object> metadata;

  @Deprecated
  @Schema(description = "Deprecated: ignored; derived from line module types.", deprecated = true)
  private ModuleType moduleType;

  private LocalDate deadline;

  @Valid private List<UpdateSalesOrderLineRequest> lines = new ArrayList<>();
}
