package com.fabricmanagement.production.masterdata.recipe.infra.repository;

import com.fabricmanagement.production.masterdata.recipe.domain.RecipeComponent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeComponentRepository extends JpaRepository<RecipeComponent, UUID> {

  List<RecipeComponent> findByRecipeIdAndIsActiveTrueOrderByDisplayOrder(UUID recipeId);

  @Modifying
  @Query(
      "UPDATE RecipeComponent rc SET rc.isActive = false, rc.deletedAt = CURRENT_TIMESTAMP WHERE rc.recipeId = :recipeId")
  void softDeleteByRecipeId(@Param("recipeId") UUID recipeId);
}
