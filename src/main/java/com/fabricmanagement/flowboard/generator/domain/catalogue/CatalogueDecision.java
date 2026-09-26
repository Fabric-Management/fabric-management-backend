package com.fabricmanagement.flowboard.generator.domain.catalogue;

import java.util.List;
import java.util.UUID;

/** Result of reconciling one catalogue entry in one tenant (ticket §5). */
public record CatalogueDecision(Kind kind, UUID adoptId, List<CatalogueFinding> findings) {

  public enum Kind {
    /** A keyed row exists in any state; the tenant's state wins. */
    R0_KEYED,
    /** No candidate; insert a copy. */
    R1_INSERT,
    /** Exactly one seeded candidate; set its key only. */
    R2_ADOPT,
    /** Anything else; no write. */
    R3_NO_WRITE
  }

  public CatalogueDecision {
    findings = List.copyOf(findings);
  }
}
