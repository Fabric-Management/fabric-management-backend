package com.fabricmanagement.production.masterdata.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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
public class RecipeRequest {

  @NotEmpty(message = "Recipe must have at least one component")
  @Valid
  private List<RecipeComponentDto> components;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RecipeComponentDto {

    @NotNull(message = "Fiber ID is mandatory")
    private UUID fiberId;

    @NotNull(message = "Fiber name is mandatory")
    private String fiberName;

    @NotNull(message = "Fiber ISO code is mandatory")
    private String fiberIsoCode;

    @NotNull(message = "Percentage is mandatory")
    @DecimalMin(value = "0.01", message = "Percentage must be greater than 0")
    @DecimalMax(value = "100.00", message = "Percentage cannot exceed 100")
    private BigDecimal percentage;

    private String certification;

    private String origin;
  }
}
