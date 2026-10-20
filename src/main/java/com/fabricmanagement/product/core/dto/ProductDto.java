package com.fabricmanagement.product.core.dto;

import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Product DTO - Data transfer object for Product entity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {

  private UUID id;
  private UUID tenantId;
  private String uid;

  @Schema(description = "The product's own name; null for fibre and yarn, named by their record")
  private String name;

  @Schema(description = "Free description of the product", nullable = true)
  private String description;

  @Schema(
      description =
          "Human-readable name: the fibre or yarn master record's name, else the product's own"
              + " name, else the uid")
  private String displayName;

  private ProductType productType;
  private String unit;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static ProductDto from(Product entity) {
    return ProductDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .name(entity.getName())
        .description(entity.getDescription())
        .displayName(entity.getName() != null ? entity.getName() : entity.getUid())
        .productType(entity.getProductType())
        .unit(entity.getUnit())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
