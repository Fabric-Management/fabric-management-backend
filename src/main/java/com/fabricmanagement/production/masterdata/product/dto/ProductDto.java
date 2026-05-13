package com.fabricmanagement.production.masterdata.product.dto;

import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
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
        .productType(entity.getProductType())
        .unit(entity.getUnit())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
