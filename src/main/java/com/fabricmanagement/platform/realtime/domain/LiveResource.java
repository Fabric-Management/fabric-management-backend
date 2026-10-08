package com.fabricmanagement.platform.realtime.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * What one live connection watches: the kind chosen by the server-side source (never by the client)
 * and the resource id the caller was authorised for.
 */
public record LiveResource(String type, UUID id) {

  public LiveResource {
    Objects.requireNonNull(id, "id");
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("A live resource needs its type");
    }
  }
}
