package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "StartWorkOrderProductionRequest")
public class StartProductionRequest {

  /**
   * The Product that the blended output batch will be linked to. This is NOT the recipe ID — it
   * must be a valid Product entity ID. Example: the Yarn product produced from this blend.
   */
  @NotNull(message = "Output product ID is required")
  private UUID outputProductId;

  /**
   * The product type of the output batch (e.g. YARN, FABRIC). Must correspond to the
   * outputProductId's actual type.
   */
  @NotNull(message = "Output product type is required")
  private ProductType outputProductType;

  /** Optional remarks for the generated output batch. */
  private String remarks;
}
