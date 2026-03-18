package com.fabricmanagement.production.masterdata.recipe.api.controller;

import com.fabricmanagement.production.masterdata.recipe.app.RecipeService;
import com.fabricmanagement.production.masterdata.recipe.dto.RecipeRequest;
import com.fabricmanagement.production.masterdata.recipe.dto.RecipeResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/recipes")
@RequiredArgsConstructor
public class RecipeController {

  private final RecipeService recipeService;

  @GetMapping("/{id}")
  public RecipeResponse getRecipe(@PathVariable UUID id) {
    return recipeService.getRecipe(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RecipeResponse createRecipe(@RequestBody @Valid RecipeRequest request) {
    return recipeService.createRecipe(request);
  }

  @PutMapping("/{id}")
  public RecipeResponse updateRecipe(
      @PathVariable UUID id, @RequestBody @Valid RecipeRequest request) {
    // Note: this effectively creates a new recipe version under the hood
    // and archives the old one based on Phase 1 logic.
    return recipeService.updateRecipe(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRecipe(@PathVariable UUID id) {
    recipeService.deleteRecipe(id);
  }
}
