package com.fabricmanagement.production.masterdata.recipe.dto;

import com.fabricmanagement.production.masterdata.recipe.domain.RecipeComponentNode;
import com.fabricmanagement.production.masterdata.recipe.domain.RecipeStatus;
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
public class RecipeResponse {
  private UUID id;
  private String uid;
  private String name;
  private String isoCode;
  private RecipeStatus status;
  private Integer recipeVersion;
  private UUID parentRecipeId;
  private List<RecipeComponentNode> components;
}
