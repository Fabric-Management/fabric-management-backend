package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One declared permission; inclusion does not grant it to the caller.")
public record PermissionCatalogueEntryDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PermissionKey key,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "sales") String resource,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "read") String action,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Whether a backend or frontend enforcement point currently consumes the pair.")
        boolean enforced) {

  public static PermissionCatalogueEntryDto from(PermissionKey key) {
    return new PermissionCatalogueEntryDto(key, key.resource(), key.action(), key.enforced());
  }
}
