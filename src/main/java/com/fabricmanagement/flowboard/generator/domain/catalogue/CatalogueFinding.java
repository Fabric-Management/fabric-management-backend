package com.fabricmanagement.flowboard.generator.domain.catalogue;

import java.util.List;
import java.util.UUID;

/**
 * A reconciliation outcome that needs an operator (ticket §5): R3 or R0's {@code UNKEYED_SIBLING}.
 * Findings never cause a write and never fail startup.
 */
public record CatalogueFinding(String catalogKey, String reason, List<UUID> candidateIds) {

  public static final String UNKEYED_SIBLING = "UNKEYED_SIBLING";
  public static final String MULTIPLE_CANDIDATES = "MULTIPLE_CANDIDATES";
  public static final String SOFT_DELETED_UNKEYED = "SOFT_DELETED_UNKEYED";
  public static final String SIGNATURE_MISMATCH = "SIGNATURE_MISMATCH:";

  public CatalogueFinding {
    candidateIds = List.copyOf(candidateIds);
  }
}
