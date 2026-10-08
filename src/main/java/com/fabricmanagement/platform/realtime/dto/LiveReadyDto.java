package com.fabricmanagement.platform.realtime.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/** The first frame of every connection: read the resource's current state now. */
@Schema(
    name = "LiveReadyDto",
    description =
        "Data of the `ready` frame, always the first frame of a connection. The client reads the"
            + " current state of the resource (and its editors when it has presence); nothing"
            + " missed before this connection is replayed.")
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
        String revision,
    @Schema(
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            minLength = 1,
            example = "p3f9c2a6e1b7d40c8a5e9f1b2c3d4e5f6",
            description =
                "Opaque marker of who edited the resource when it was read; compare for equality"
                    + " only. Absent for a resource without presence. It changes when an edit"
                    + " session opens, closes or expires; the client then reads the editors again."
                    + " It is never mixed with revision.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String presenceRevision) {

  public LiveReadyDto {
    Objects.requireNonNull(connectionId, "connectionId");
    Objects.requireNonNull(resourceId, "resourceId");
    if (revision == null || revision.isBlank()) {
      throw new IllegalArgumentException("revision must not be empty");
    }
    if (presenceRevision != null && presenceRevision.isBlank()) {
      throw new IllegalArgumentException("presenceRevision must not be blank");
    }
  }

  /** A frame of a resource without presence. */
  public LiveReadyDto(UUID connectionId, UUID resourceId, String revision) {
    this(connectionId, resourceId, revision, null);
  }
}
