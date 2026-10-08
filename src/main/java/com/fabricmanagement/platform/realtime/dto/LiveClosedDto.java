package com.fabricmanagement.platform.realtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/** The server ends the stream after this frame. */
@Schema(
    name = "LiveClosedDto",
    description =
        "Data of the `closed` control frame. The server closes the stream right after it; a stream"
            + " may also end without it (network loss, a client that stopped reading).")
public record LiveClosedDto(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "uuid",
            description = "Id of the connection being closed.")
        UUID connectionId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Why the stream closes.")
        LiveCloseReason reason) {

  public LiveClosedDto {
    Objects.requireNonNull(connectionId, "connectionId");
    Objects.requireNonNull(reason, "reason");
  }
}
