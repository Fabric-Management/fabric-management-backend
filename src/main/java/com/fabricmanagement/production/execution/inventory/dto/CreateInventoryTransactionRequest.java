package com.fabricmanagement.production.execution.inventory.dto;

import com.fabricmanagement.production.execution.inventory.domain.InventoryTransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInventoryTransactionRequest {

  private Long version;

  @NotNull(message = "Batch ID is required")
  private UUID batchId;

  @NotNull(message = "Transaction type is required")
  private InventoryTransactionType transactionType;

  @NotNull(message = "Quantity is required")
  @DecimalMin(value = "0.001", message = "Quantity must be at least 0.001")
  private BigDecimal quantity;

  @NotBlank(message = "Unit is required")
  private String unit;

  private Instant transactionDate;

  private UUID referenceId;
  private String referenceType;
  private String reason;
  private String remarks;
}
