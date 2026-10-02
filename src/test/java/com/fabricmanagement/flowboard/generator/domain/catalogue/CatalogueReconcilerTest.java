package com.fabricmanagement.flowboard.generator.domain.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TASK-TEMPLATE-TENANCY-1 §5: R0 → R1 → R2 → R3, first match decides. */
class CatalogueReconcilerTest {

  private static final TaskTemplateCatalogue QUOTE =
      TaskTemplateCatalogue.QUOTE_SEND_REQUESTED__APPROVAL;
  private static final TaskTemplateCatalogue RECIPE =
      TaskTemplateCatalogue.WORK_ORDER_RECIPE_ASSIGNMENT_NEEDED__RECIPE_ASSIGNMENT;
  private static final TaskTemplateCatalogue SALES =
      TaskTemplateCatalogue.SALES_ORDER_CONFIRMED__PLANNING;

  @Test
  void noCandidateInserts() {
    CatalogueDecision decision = CatalogueReconciler.decide(QUOTE, List.of());

    assertThat(decision.kind()).isEqualTo(CatalogueDecision.Kind.R1_INSERT);
    assertThat(decision.findings()).isEmpty();
  }

  @Test
  void keyedRowWinsInAnyState() {
    CatalogueCandidate deletedKeyed = keyed(QUOTE).withDeletedAt(Instant.now()).build();

    CatalogueDecision decision = CatalogueReconciler.decide(QUOTE, List.of(deletedKeyed));

    assertThat(decision.kind()).isEqualTo(CatalogueDecision.Kind.R0_KEYED);
    assertThat(decision.findings()).isEmpty();
  }

  @Test
  void keyedRowWithUnkeyedSiblingIsR0WithOneSiblingFindingAndNoR3() {
    CatalogueCandidate keyed = keyed(QUOTE).build();
    CatalogueCandidate sibling = seed(QUOTE).build();

    CatalogueDecision decision = CatalogueReconciler.decide(QUOTE, List.of(keyed, sibling));

    assertThat(decision.kind()).isEqualTo(CatalogueDecision.Kind.R0_KEYED);
    assertThat(decision.findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.reason()).isEqualTo(CatalogueFinding.UNKEYED_SIBLING);
              assertThat(finding.candidateIds()).containsExactly(keyed.id(), sibling.id());
            });
  }

  @Test
  void singleSeededRowIsAdopted() {
    CatalogueCandidate seeded = seed(QUOTE).build();

    CatalogueDecision decision = CatalogueReconciler.decide(QUOTE, List.of(seeded));

    assertThat(decision.kind()).isEqualTo(CatalogueDecision.Kind.R2_ADOPT);
    assertThat(decision.adoptId()).isEqualTo(seeded.id());
  }

  @Test
  void seedUidRulesAreHonoured() {
    assertThat(CatalogueReconciler.decide(RECIPE, List.of(seed(RECIPE).build())).kind())
        .isEqualTo(CatalogueDecision.Kind.R2_ADOPT);
    assertThat(
            CatalogueReconciler.decide(RECIPE, List.of(seed(RECIPE).withUid("OTHER").build()))
                .findings())
        .singleElement()
        .extracting(CatalogueFinding::reason)
        .isEqualTo(CatalogueFinding.SIGNATURE_MISMATCH + "uid");
    assertThat(
            CatalogueReconciler.decide(SALES, List.of(seed(SALES).withUid("X-TMPL-1").build()))
                .kind())
        .isEqualTo(CatalogueDecision.Kind.R3_NO_WRITE);
  }

  @Test
  void apiCreatedTwinStaysR3OnProvenance() {
    // Identical content, name, event/task type and version 0, but written through JPA:
    // created_by is never null there, and BaseEntity mints a <tenant>-TMPL-xxxx uid.
    CatalogueCandidate apiTwin =
        seed(QUOTE).withCreatedBy(UUID.randomUUID()).withUid("ACME-001-TMPL-1A2B3C4D").build();

    CatalogueDecision decision = CatalogueReconciler.decide(QUOTE, List.of(apiTwin));

    assertThat(decision.kind()).isEqualTo(CatalogueDecision.Kind.R3_NO_WRITE);
    assertThat(decision.findings())
        .singleElement()
        .extracting(CatalogueFinding::reason)
        .isEqualTo(CatalogueFinding.SIGNATURE_MISMATCH + "createdBy");
  }

  @Test
  void editedOrDescribedOrRenamedSeedIsR3() {
    assertThat(reason(seed(QUOTE).withVersion(1).build()))
        .isEqualTo(CatalogueFinding.SIGNATURE_MISMATCH + "version");
    assertThat(reason(seed(QUOTE).withUpdatedBy(UUID.randomUUID()).build()))
        .isEqualTo(CatalogueFinding.SIGNATURE_MISMATCH + "updatedBy");
    assertThat(reason(seed(QUOTE).withDescription("mine").build()))
        .isEqualTo(CatalogueFinding.SIGNATURE_MISMATCH + "description");
    assertThat(reason(seed(QUOTE).withName("Renamed").build()))
        .isEqualTo(CatalogueFinding.SIGNATURE_MISMATCH + "name");
    assertThat(reason(seed(QUOTE).withFingerprint("0".repeat(32)).build()))
        .isEqualTo(CatalogueFinding.SIGNATURE_MISMATCH + "fingerprint");
  }

  @Test
  void twoCandidatesAreR3() {
    CatalogueDecision decision =
        CatalogueReconciler.decide(QUOTE, List.of(seed(QUOTE).build(), seed(QUOTE).build()));

    assertThat(decision.kind()).isEqualTo(CatalogueDecision.Kind.R3_NO_WRITE);
    assertThat(decision.findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.reason()).isEqualTo(CatalogueFinding.MULTIPLE_CANDIDATES);
              assertThat(finding.candidateIds()).hasSize(2);
            });
  }

  @Test
  void softDeletedUnkeyedCandidateIsR3AndNeverRevived() {
    CatalogueDecision decision =
        CatalogueReconciler.decide(
            QUOTE, List.of(seed(QUOTE).withDeletedAt(Instant.now()).build()));

    assertThat(decision.kind()).isEqualTo(CatalogueDecision.Kind.R3_NO_WRITE);
    assertThat(decision.findings())
        .singleElement()
        .extracting(CatalogueFinding::reason)
        .isEqualTo(CatalogueFinding.SOFT_DELETED_UNKEYED);
  }

  @Test
  void unrelatedRowsAreIgnored() {
    CatalogueCandidate otherJob = seed(SALES).build();
    CatalogueCandidate otherKey = keyed(SALES).build();

    assertThat(CatalogueReconciler.decide(QUOTE, List.of(otherJob, otherKey)).kind())
        .isEqualTo(CatalogueDecision.Kind.R1_INSERT);
  }

  private static String reason(CatalogueCandidate row) {
    CatalogueDecision decision = CatalogueReconciler.decide(QUOTE, List.of(row));
    assertThat(decision.kind()).isEqualTo(CatalogueDecision.Kind.R3_NO_WRITE);
    return decision.findings().getFirst().reason();
  }

  /** A row carrying the entry's full seed signature. */
  private static Builder seed(TaskTemplateCatalogue entry) {
    String uid =
        switch (entry) {
          case QUOTE_SEND_REQUESTED__APPROVAL -> UUID.randomUUID().toString();
          case WORK_ORDER_RECIPE_ASSIGNMENT_NEEDED__RECIPE_ASSIGNMENT -> "SYS-TMPL-RECIPE-ASSIGN";
          default -> null;
        };
    return new Builder(entry).withUid(uid);
  }

  private static Builder keyed(TaskTemplateCatalogue entry) {
    return new Builder(entry)
        .withKey(entry.key())
        .withCreatedBy(UUID.randomUUID())
        .withUid("T-TMPL-" + UUID.randomUUID());
  }

  private static final class Builder {
    private final TaskTemplateCatalogue entry;
    private String key;
    private String name;
    private String description;
    private String uid;
    private UUID createdBy;
    private UUID updatedBy;
    private long version;
    private Instant deletedAt;
    private String fingerprint;

    Builder(TaskTemplateCatalogue entry) {
      this.entry = entry;
      this.name = entry.seedName();
      this.fingerprint = entry.seedFingerprint();
    }

    Builder withKey(String value) {
      key = value;
      return this;
    }

    Builder withName(String value) {
      name = value;
      return this;
    }

    Builder withDescription(String value) {
      description = value;
      return this;
    }

    Builder withUid(String value) {
      uid = value;
      return this;
    }

    Builder withCreatedBy(UUID value) {
      createdBy = value;
      return this;
    }

    Builder withUpdatedBy(UUID value) {
      updatedBy = value;
      return this;
    }

    Builder withVersion(long value) {
      version = value;
      return this;
    }

    Builder withDeletedAt(Instant value) {
      deletedAt = value;
      return this;
    }

    Builder withFingerprint(String value) {
      fingerprint = value;
      return this;
    }

    CatalogueCandidate build() {
      return new CatalogueCandidate(
          UUID.randomUUID(),
          key,
          entry.eventType(),
          entry.taskType().name(),
          name,
          description,
          uid,
          createdBy,
          updatedBy,
          version,
          deletedAt,
          fingerprint);
    }
  }
}
