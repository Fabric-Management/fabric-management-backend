package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.BatchAttribute;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberAttributeDto;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAttributeDto {

  private UUID id;
  private UUID batchId;
  private UUID attributeId;
  private FiberAttributeDto attribute;
  private String value;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static BatchAttributeDto from(BatchAttribute entity) {
    return BatchAttributeDto.builder()
        .id(entity.getId())
        .batchId(entity.getBatch() != null ? entity.getBatch().getId() : null)
        .attributeId(entity.getAttribute() != null ? entity.getAttribute().getId() : null)
        .attribute(
            entity.getAttribute() != null ? FiberAttributeDto.from(entity.getAttribute()) : null)
        .value(entity.getValue())
        .isActive(entity.getIsActive())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
