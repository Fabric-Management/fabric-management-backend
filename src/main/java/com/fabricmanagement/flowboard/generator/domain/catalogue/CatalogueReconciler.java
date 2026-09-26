package com.fabricmanagement.flowboard.generator.domain.catalogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure R0 → R1 → R2 → R3 decision for one catalogue entry in one tenant (TASK-TEMPLATE-TENANCY-1
 * §5). The first matching case decides. No I/O; the callers read the rows and apply the decision.
 */
public final class CatalogueReconciler {

  private CatalogueReconciler() {}

  /**
   * @param entry the catalogue entry
   * @param tenantRows rows of the target tenant; rows unrelated to {@code entry} are ignored
   */
  public static CatalogueDecision decide(
      TaskTemplateCatalogue entry, List<CatalogueCandidate> tenantRows) {
    List<CatalogueCandidate> keyed = new ArrayList<>();
    List<CatalogueCandidate> unkeyed = new ArrayList<>();
    for (CatalogueCandidate row : tenantRows) {
      if (entry.key().equals(row.catalogKey())) {
        keyed.add(row);
      } else if (row.catalogKey() == null
          && entry.eventType().equals(row.eventType())
          && entry.taskType().name().equals(row.taskType())) {
        unkeyed.add(row);
      }
    }

    // R0: a keyed row exists in any state; unkeyed siblings are reported, never written.
    if (!keyed.isEmpty()) {
      List<CatalogueFinding> findings =
          unkeyed.isEmpty()
              ? List.of()
              : List.of(
                  new CatalogueFinding(
                      entry.key(), CatalogueFinding.UNKEYED_SIBLING, ids(keyed, unkeyed)));
      return new CatalogueDecision(CatalogueDecision.Kind.R0_KEYED, null, findings);
    }

    // R1: nothing there.
    if (unkeyed.isEmpty()) {
      return new CatalogueDecision(CatalogueDecision.Kind.R1_INSERT, null, List.of());
    }

    // R3: more than one candidate.
    if (unkeyed.size() > 1) {
      return r3(entry, CatalogueFinding.MULTIPLE_CANDIDATES, unkeyed);
    }

    CatalogueCandidate only = unkeyed.getFirst();
    if (only.deletedAt() != null) {
      return r3(entry, CatalogueFinding.SOFT_DELETED_UNKEYED, unkeyed);
    }
    String mismatch = signatureMismatch(entry, only);
    if (mismatch != null) {
      return r3(entry, CatalogueFinding.SIGNATURE_MISMATCH + mismatch, unkeyed);
    }

    // R2: exactly one candidate carrying the full seed signature.
    return new CatalogueDecision(CatalogueDecision.Kind.R2_ADOPT, only.id(), List.of());
  }

  /**
   * The first field in which {@code row} misses the seed signature of {@code entry}, or {@code
   * null} when it carries the full signature. Provenance fields come first: a row written through
   * JPA always has a non-null {@code created_by}, so it can never pass.
   */
  static String signatureMismatch(TaskTemplateCatalogue entry, CatalogueCandidate row) {
    if (row.catalogKey() != null) {
      return "catalogKey";
    }
    if (row.createdBy() != null) {
      return "createdBy";
    }
    if (row.updatedBy() != null) {
      return "updatedBy";
    }
    if (row.version() != 0L) {
      return "version";
    }
    if (row.deletedAt() != null) {
      return "deletedAt";
    }
    if (row.description() != null) {
      return "description";
    }
    if (!Objects.equals(entry.seedName(), row.name())) {
      return "name";
    }
    if (!entry.uidMatchesSeed(row.uid())) {
      return "uid";
    }
    if (!Objects.equals(entry.seedFingerprint(), row.fingerprint())) {
      return "fingerprint";
    }
    return null;
  }

  private static CatalogueDecision r3(
      TaskTemplateCatalogue entry, String reason, List<CatalogueCandidate> candidates) {
    return new CatalogueDecision(
        CatalogueDecision.Kind.R3_NO_WRITE,
        null,
        List.of(new CatalogueFinding(entry.key(), reason, ids(candidates, List.of()))));
  }

  private static List<UUID> ids(List<CatalogueCandidate> first, List<CatalogueCandidate> second) {
    List<UUID> ids = new ArrayList<>();
    first.forEach(row -> ids.add(row.id()));
    second.forEach(row -> ids.add(row.id()));
    return ids;
  }
}
