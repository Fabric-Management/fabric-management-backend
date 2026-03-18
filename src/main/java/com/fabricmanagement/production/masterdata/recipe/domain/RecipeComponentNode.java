package com.fabricmanagement.production.masterdata.recipe.domain;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single component in the recipe's JSONB structure. This is used for fast reads
 * without requiring joins.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeComponentNode {
  private UUID fiberId;
  private String fiberName;
  private String fiberIsoCode;
  private BigDecimal percentage;
  private String certification;
  private String origin;
}
