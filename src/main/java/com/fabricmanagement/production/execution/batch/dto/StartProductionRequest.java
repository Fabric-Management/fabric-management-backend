package com.fabricmanagement.production.execution.batch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to start production on a batch.
 *
 * <p>Transfers the batch to a machine location and sets its status to IN_PROGRESS. The batch is NOT
 * consumed — it remains in the system at the machine location until production completes and a real
 * consumption + lineage is recorded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "StartBatchProductionRequest")
public class StartProductionRequest {

  @NotNull(message = "Machine location ID is required")
  private UUID machineLocationId;

  private String remarks;
}
