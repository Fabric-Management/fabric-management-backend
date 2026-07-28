package com.fabricmanagement.sales.ownership.domain;

import java.util.Objects;
import java.util.UUID;

public record ActorRef(AssignmentActorType type, UUID userId, String systemCode) {

  public ActorRef {
    Objects.requireNonNull(type, "type must not be null");
    boolean userActor = type == AssignmentActorType.USER && userId != null && systemCode == null;
    boolean systemActor =
        type == AssignmentActorType.SYSTEM
            && userId == null
            && systemCode != null
            && !systemCode.isBlank();
    if (!userActor && !systemActor) {
      throw new IllegalArgumentException(
          "ActorRef must contain exactly the identifier required by its actor type");
    }
  }

  public static ActorRef user(UUID userId) {
    return new ActorRef(
        AssignmentActorType.USER, Objects.requireNonNull(userId, "userId must not be null"), null);
  }

  public static ActorRef system(String systemCode) {
    return new ActorRef(
        AssignmentActorType.SYSTEM,
        null,
        Objects.requireNonNull(systemCode, "systemCode must not be null"));
  }
}
