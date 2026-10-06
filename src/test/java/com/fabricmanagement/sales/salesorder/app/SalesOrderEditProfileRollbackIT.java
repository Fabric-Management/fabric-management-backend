package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * S16.7: the profile stored on a line differs from the one the save resolved (an injected
 * inconsistency). The real persistence step runs first; then the save is refused as an internal
 * error and its transaction is rolled back as a whole: no profile version, line change, header
 * change, receipt or history remains. The spy replaces nothing in production code; it only alters
 * what the real method returns. A spy bean makes this class its own Spring context.
 */
class SalesOrderEditProfileRollbackIT extends SalesOrderEditItSupport {

  @MockitoSpyBean private RequirementProfileService profiles;

  @BeforeEach
  void injectAnInconsistentProfile() {
    doAnswer(
            invocation -> {
              RequirementProfileSnapshot stored =
                  (RequirementProfileSnapshot) invocation.callRealMethod();
              return withFingerprint(stored, "0".repeat(64));
            })
        .when(profiles)
        .applyResolved(any(), any());
  }

  @Test
  @DisplayName("S16.7: an existing line's inconsistent profile rolls the whole save back")
  void inconsistentProfileOfALineRollsTheSaveBack() {
    long version = orderVersion();
    long l1Version = lineVersion(l1);
    int versions = profileVersions();

    Object result =
        save(
            actorB,
            withLines(
                body(UUID.randomUUID(), open(actorB).baseId(), "notes", set("Urgent")),
                List.of(
                    update(l1, "specification", set(specification("line-1"))),
                    update(l2, "productDesc", set("Selvedge note")))));

    assertThat(failureCode(result)).isEqualTo("EDIT_PROFILE_INCONSISTENT");
    assertThat(((DomainException) result).getHttpStatus()).isEqualTo(500);
    verify(profiles, atLeastOnce()).applyResolved(any(), any());
    assertNothingKept(version, versions);
    assertThat(lineVersion(l1)).isEqualTo(l1Version);
    assertThat(lineFingerprint(l1)).isNull();
    assertThat(lineText(l2, "product_desc")).isNull();
  }

  @Test
  @DisplayName("S16.7: a new line's inconsistent profile rolls the whole save back, line included")
  void inconsistentProfileOfANewLineRollsTheSaveBack() {
    long version = orderVersion();
    int versions = profileVersions();

    Object result =
        save(
            actorB,
            withLines(
                body(UUID.randomUUID(), open(actorB).baseId()),
                List.of(
                    add(
                        UUID.randomUUID(),
                        p1,
                        "quantity",
                        set(quantity("200", "M")),
                        "specification",
                        set(specification("new-line"))))));

    assertThat(failureCode(result)).isEqualTo("EDIT_PROFILE_INCONSISTENT");
    assertNothingKept(version, versions);
    assertThat(activeLines()).isEqualTo(2);
  }

  @Test
  @DisplayName("Without the injected inconsistency the same save applies (control)")
  void consistentProfileApplies() {
    org.mockito.Mockito.reset(profiles);

    Object result =
        save(
            actorB,
            withLines(
                body(UUID.randomUUID(), open(actorB).baseId()),
                List.of(update(l1, "specification", set(specification("line-1"))))));

    assertThat(result)
        .isInstanceOfSatisfying(
            com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult.class,
            saved -> assertThat(saved.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED));
    assertThat(lineFingerprint(l1)).isNotNull();
  }

  private void assertNothingKept(long version, int versions) {
    assertThat(profileVersions()).isEqualTo(versions);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(orderText("notes")).isNull();
    assertThat(receipts()).isZero();
    assertThat(historyRows()).isZero();
  }

  private String lineFingerprint(UUID lineId) {
    return lineText(lineId, "requirement_profile_fingerprint");
  }

  private String lineText(UUID lineId, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + "::text FROM sales_ord.sales_order_line WHERE id = ?",
        String.class,
        lineId);
  }

  private static RequirementProfileSnapshot withFingerprint(
      RequirementProfileSnapshot snapshot, String fingerprint) {
    return new RequirementProfileSnapshot(
        snapshot.profileId(),
        snapshot.profileVersion(),
        snapshot.basis(),
        snapshot.scopeVersion(),
        snapshot.resolutionRuleVersion(),
        snapshot.scope(),
        snapshot.facets(),
        snapshot.unmodelledConstraints(),
        snapshot.deviations(),
        snapshot.pinnedSource(),
        snapshot.complete(),
        snapshot.incompleteReasons(),
        fingerprint);
  }

  /** A line-explicit requirement profile; the decision reference makes it distinct. */
  private Map<String, Object> specification(String decisionReference) {
    Map<String, Object> basis =
        pairs(
            "kind",
            "LINE_EXPLICIT",
            "actorId",
            actorA.id(),
            "decidedAt",
            "2026-10-01T10:00:00Z",
            "decisionReference",
            decisionReference);
    Map<String, Object> profile =
        pairs(
            "basis",
            basis,
            "scopeVersion",
            "client-scope",
            "resolutionRuleVersion",
            "client-rule",
            "scope",
            List.of(),
            "facets",
            List.of(),
            "unmodelledConstraints",
            List.of(),
            "deviations",
            List.of());
    return pairs("moduleSpecs", Map.of(), "requirementProfile", profile);
  }
}
