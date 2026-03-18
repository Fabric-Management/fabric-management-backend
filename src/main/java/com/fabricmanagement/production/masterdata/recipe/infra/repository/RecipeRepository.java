package com.fabricmanagement.production.masterdata.recipe.infra.repository;

import com.fabricmanagement.production.masterdata.recipe.domain.Recipe;
import com.fabricmanagement.production.masterdata.recipe.domain.RecipeStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

  /** Retrieves an active recipe by exact match of its automatically generated name. */
  @Query(
      "SELECT r FROM Recipe r WHERE r.name = :name AND r.status = 'ACTIVE' AND r.isActive = true")
  Optional<Recipe> findActiveByName(@Param("name") String name);

  /**
   * Used for duplicate detection before creating or updating a recipe. The canonical name is
   * deterministic — same components always produce the same name.
   */
  boolean existsByNameAndStatusAndIsActiveTrue(String name, RecipeStatus status);
}
