package com.fabricmanagement.production.masterdata.recipe.app;

import com.fabricmanagement.production.masterdata.recipe.domain.Recipe;
import com.fabricmanagement.production.masterdata.recipe.domain.RecipeComponent;
import com.fabricmanagement.production.masterdata.recipe.domain.RecipeComponentNode;
import com.fabricmanagement.production.masterdata.recipe.domain.RecipeStatus;
import com.fabricmanagement.production.masterdata.recipe.domain.exception.RecipeDomainException;
import com.fabricmanagement.production.masterdata.recipe.dto.RecipeRequest;
import com.fabricmanagement.production.masterdata.recipe.dto.RecipeResponse;
import com.fabricmanagement.production.masterdata.recipe.infra.repository.RecipeComponentRepository;
import com.fabricmanagement.production.masterdata.recipe.infra.repository.RecipeRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain Service for Recipes. Creates both JSONB nodes on Recipe and physical relational items in
 * prod_recipe_component. Implements Recipe versioning (ACTIVE vs ARCHIVED).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

  private final RecipeRepository recipeRepository;
  private final RecipeComponentRepository recipeComponentRepository;

  /** Retrieves an active recipe by its UUID. */
  public RecipeResponse getRecipe(UUID id) {
    Recipe recipe = findEntityById(id);
    return mapToResponse(recipe);
  }

  /** Creates a brand new recipe (Version 1). */
  @Transactional
  public RecipeResponse createRecipe(RecipeRequest request) {
    Recipe recipe = Recipe.builder().status(RecipeStatus.ACTIVE).recipeVersion(1).build();

    // Attach request nodes
    recipe.setComponents(mapRequestToNodes(request));

    // Validates if percentage == 100.00
    recipe.validatePercentageSum();

    // Auto-generates deterministic name & isoCode from its components
    recipe.recalculateNameAndIsoCode();

    // Verify duplication
    if (recipeRepository.existsByNameAndStatusAndIsActiveTrue(
        recipe.getName(), RecipeStatus.ACTIVE)) {
      throw new RecipeDomainException(
          String.format(
              "An ACTIVE recipe with the exact same formulation already exists: %s",
              recipe.getName()));
    }

    // Save recipe first
    recipe = recipeRepository.save(recipe);

    // Write relations side-by-side to RecipeComponent table
    writeComponentsToTable(recipe, request);

    return mapToResponse(recipe);
  }

  /**
   * Updates an existing active recipe. Mimicking Phase 1.1 documentation: "Mevcut recipe ARCHIVED
   * olur. Yeni recipe oluşturulur, version bir artar"
   */
  @Transactional
  public RecipeResponse updateRecipe(UUID existingId, RecipeRequest request) {
    Recipe existing = findEntityById(existingId);

    if (existing.getStatus() == RecipeStatus.ARCHIVED) {
      throw new RecipeDomainException(
          "Cannot update an ARCHIVED recipe. Please create a new one instead.");
    }

    // Form the new recipe first to see what it will look like
    List<RecipeComponentNode> newNodes = mapRequestToNodes(request);

    // Create a temporary object to calculate name and iso
    Recipe dummy = Recipe.builder().components(newNodes).build();
    dummy.validatePercentageSum();
    dummy.recalculateNameAndIsoCode();

    // If perfectly matches original, do nothing
    if (dummy.getName().equals(existing.getName())
        && existing.getComponents().size() == newNodes.size()) {
      return mapToResponse(existing);
    }

    if (recipeRepository.existsByNameAndStatusAndIsActiveTrue(
        dummy.getName(), RecipeStatus.ACTIVE)) {
      throw new RecipeDomainException(
          String.format(
              "Update failed. An ACTIVE recipe with the exact same formulation already exists: %s",
              dummy.getName()));
    }

    // Mark old as archived
    existing.setStatus(RecipeStatus.ARCHIVED);
    recipeRepository.save(existing);

    // Create new Version
    Recipe newVersion =
        Recipe.builder()
            .status(RecipeStatus.ACTIVE)
            .recipeVersion(existing.getRecipeVersion() + 1)
            .parentRecipeId(existing.getId())
            .components(newNodes)
            .build();

    newVersion.validatePercentageSum();
    newVersion.recalculateNameAndIsoCode();

    newVersion = recipeRepository.save(newVersion);
    writeComponentsToTable(newVersion, request);

    return mapToResponse(newVersion);
  }

  @Transactional
  public void deleteRecipe(UUID id) {
    Recipe recipe = findEntityById(id);
    recipe.delete();
    recipe.setStatus(RecipeStatus.ARCHIVED); // Safety default
    recipeRepository.save(recipe);

    recipeComponentRepository.softDeleteByRecipeId(id);
  }

  private Recipe findEntityById(UUID id) {
    return recipeRepository
        .findById(id)
        .orElseThrow(() -> new RecipeDomainException("Recipe not found with id: " + id));
  }

  // Mapper: DTO -> Entity JSONB Node
  private List<RecipeComponentNode> mapRequestToNodes(RecipeRequest request) {
    return request.getComponents().stream()
        .map(
            c ->
                RecipeComponentNode.builder()
                    .fiberId(c.getFiberId())
                    .fiberName(c.getFiberName())
                    .fiberIsoCode(c.getFiberIsoCode())
                    .percentage(c.getPercentage())
                    .certification(c.getCertification())
                    .origin(c.getOrigin())
                    .build())
        .collect(Collectors.toList());
  }

  // Relational side: Saves List elements to prod_recipe_component
  private void writeComponentsToTable(Recipe recipe, RecipeRequest request) {
    AtomicInteger index = new AtomicInteger(1);

    List<RecipeComponent> relations =
        request.getComponents().stream()
            .map(
                c ->
                    RecipeComponent.builder()
                        .recipeId(recipe.getId())
                        .fiberId(c.getFiberId())
                        .fiberName(c.getFiberName())
                        .fiberIsoCode(c.getFiberIsoCode())
                        .percentage(c.getPercentage())
                        .certification(c.getCertification())
                        .origin(c.getOrigin())
                        .displayOrder(index.getAndIncrement())
                        .build())
            .collect(Collectors.toList());

    recipeComponentRepository.saveAll(relations);
  }

  private RecipeResponse mapToResponse(Recipe recipe) {
    return RecipeResponse.builder()
        .id(recipe.getId())
        .uid(recipe.getUid())
        .name(recipe.getName())
        .isoCode(recipe.getIsoCode())
        .status(recipe.getStatus())
        .recipeVersion(recipe.getRecipeVersion())
        .parentRecipeId(recipe.getParentRecipeId())
        .components(recipe.getComponents()) // Uses the fast JSONB retrieval
        .build();
  }
}
