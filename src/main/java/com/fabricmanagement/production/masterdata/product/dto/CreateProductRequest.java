package com.fabricmanagement.production.masterdata.product.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
}
