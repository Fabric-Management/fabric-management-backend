package com.fabricmanagement.platform.realtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/** The first frame of every connection: read the resource's current state now. */
@Schema(
    name = "LiveReadyDto",
    description =
        "Data of the `ready` frame, always the first frame of a connection. The client reads the"
            + " current state of the resource; nothing missed before this connection is replayed.")
public record LiveReadyDto(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "uuid",
            description = "Server-generated id of this connection; every connection has a new one.")
        UUID connectionId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "uuid",
            description = "The subscribed resource; for a sales order, its id.")
        UUID resourceId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            example = "12",
            description =
                "Opaque revision of the resource when it was read; compare for equality only. A"
                    + " sales order sends its version as a decimal string; 0 is valid.")
        String revision) {

  public LiveReadyDto {
    Objects.requireNonNull(connectionId, "connectionId");
    Objects.requireNonNull(resourceId, "resourceId");
    if (revision == null || revision.isBlank()) {
      throw new IllegalArgumentException("revision must not be empty");
    }
  }
}
