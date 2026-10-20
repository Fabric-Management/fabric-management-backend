package com.fabricmanagement.product.core.dto;

import com.fabricmanagement.product.core.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request for creating new product. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

  private Long version;

  @NotNull(message = "Product type is required")
  private ProductType productType;

  @NotBlank(message = "Unit is required")
  private String unit;

  @Schema(
      description =
          "The product's name. Required for FABRIC, CHEMICAL and CONSUMABLE; fibre and yarn take"
              + " their name from their own master record.",
      example = "Cotton Poplin 120gsm")
  @Size(max = 255, message = "Name must be at most 255 characters")
  private String name;

  @Schema(description = "Free description of the product", nullable = true)
  @Size(max = 4000, message = "Description must be at most 4000 characters")
  private String description;
}
