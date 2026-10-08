package com.fabricmanagement.platform.realtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/** The watched revision changed: read the current state again. */
@Schema(
    name = "LiveInvalidatedDto",
    description =
        "Data of the `invalidated` frame: the committed revision differs from the last one sent on"
            + " this connection. Several changes may arrive as one frame; the client reads the"
            + " current state and does not count frames. It does not say who changed what.")
public record LiveInvalidatedDto(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "uuid",
            description = "Id of this connection, as in its ready frame.")
        UUID connectionId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "uuid",
            description = "The subscribed resource; for a sales order, its id.")
        UUID resourceId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            example = "13",
            description =
                "Opaque committed revision now; compare for equality only. A sales order sends its"
                    + " version as a decimal string.")
        String revision) {

  public LiveInvalidatedDto {
    Objects.requireNonNull(connectionId, "connectionId");
    Objects.requireNonNull(resourceId, "resourceId");
    if (revision == null || revision.isBlank()) {
      throw new IllegalArgumentException("revision must not be empty");
    }
  }
}
