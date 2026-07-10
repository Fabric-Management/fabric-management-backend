package com.fabricmanagement.platform.user.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Event published when a profile update request is approved. */
@Getter
public class ProfileUpdateRequestApprovedEvent extends DomainEvent {

  private final UUID requestId;
  private final UUID userId;
  private final UUID reviewedBy;
  private final String profileCategory;

  public ProfileUpdateRequestApprovedEvent(
      UUID tenantId, UUID requestId, UUID userId, UUID reviewedBy, String profileCategory) {
    super(tenantId, "PROFILE_UPDATE_REQUEST_APPROVED");
    this.requestId = requestId;
    this.userId = userId;
    this.reviewedBy = reviewedBy;
    this.profileCategory = profileCategory;
  }

  @JsonCreator
  public ProfileUpdateRequestApprovedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("requestId") UUID requestId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("reviewedBy") UUID reviewedBy,
      @JsonProperty("profileCategory") String profileCategory) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PROFILE_UPDATE_REQUEST_APPROVED",
        occurredAt,
        correlationId);
    this.requestId = requestId;
    this.userId = userId;
    this.reviewedBy = reviewedBy;
    this.profileCategory = profileCategory;
  }
}
