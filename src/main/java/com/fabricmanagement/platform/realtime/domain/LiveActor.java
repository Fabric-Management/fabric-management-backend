package com.fabricmanagement.platform.realtime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Who holds a live connection, fixed when it opens (CEDIT-05 §4.1): the verified user and tenant of
 * the request and the last instant the request's access token is valid. Nothing the client states
 * afterwards changes it; a new token needs a new connection.
 */
public record LiveActor(UUID tenantId, UUID userId, Instant expiresAt) {

  public LiveActor {
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(expiresAt, "expiresAt");
  }
}
