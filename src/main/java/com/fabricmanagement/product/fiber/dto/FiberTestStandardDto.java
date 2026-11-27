package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.FiberTestStandard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "FiberTestStandardDto")
public record FiberTestStandardDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255) String name,
    @Schema(nullable = true, maxLength = 100) String revision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {

  public static FiberTestStandardDto from(FiberTestStandard standard) {
    return new FiberTestStandardDto(
        standard.getId(),
        standard.getCode(),
        standard.getName(),
        standard.getRevision().isEmpty() ? null : standard.getRevision(),
        Boolean.TRUE.equals(standard.getIsActive()));
  }
}
