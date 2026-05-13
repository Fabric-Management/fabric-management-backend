package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.BatchAttribute;
import com.fabricmanagement.production.masterdata.product.dto.ProductAttributeDto;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record BatchAttributeDto(
    UUID id,
    UUID batchId,
    UUID attributeId,
    ProductAttributeDto attribute,
    String value,
    Boolean isActive,
    Long version,
    Instant createdAt,
    Instant updatedAt) {

  public static BatchAttributeDto from(BatchAttribute entity) {
    return BatchAttributeDto.builder()
        .id(entity.getId())
        .batchId(entity.getBatch() != null ? entity.getBatch().getId() : null)
        .attributeId(entity.getAttribute() != null ? entity.getAttribute().getId() : null)
        .attribute(
            entity.getAttribute() != null ? ProductAttributeDto.from(entity.getAttribute()) : null)
        .value(entity.getValue())
        .isActive(entity.getIsActive())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
