package com.fabricmanagement.platform.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI Action Request - Structured action from AI assistant. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIActionRequest {

  /** Action name (e.g., "check_product_stock", "create_purchase_order") */
  private String action;

  /** Action parameters */
  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> parameters;

  /** Whether user confirmation is required */
  private Boolean requiresConfirmation;
}
