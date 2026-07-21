package com.fabricmanagement.production.quality.decision.app;

import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOrigin;
import java.util.UUID;

/** Server-owned decision provenance. This type is never deserialized from HTTP. */
public record TrustedDecisionContext(
    UUID actorId, QualityDecisionOrigin origin, UUID sourceEventId) {

  public static TrustedDecisionContext manual(UUID actorId) {
    return new TrustedDecisionContext(actorId, QualityDecisionOrigin.MANUAL, null);
  }

  public static TrustedDecisionContext systemRelease(UUID systemActorId) {
    return new TrustedDecisionContext(systemActorId, QualityDecisionOrigin.SYSTEM_RELEASE, null);
  }

  public static TrustedDecisionContext qcEvent(UUID actorId, UUID eventId) {
    return new TrustedDecisionContext(actorId, QualityDecisionOrigin.SYSTEM_QC_EVENT, eventId);
  }
}
