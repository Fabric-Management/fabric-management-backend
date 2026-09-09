package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.bootstrap.PermissionTemplateSeeder;
import com.fabricmanagement.common.infrastructure.security.DataScopeGuard;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.security.PermissionKey.EnforcedBy;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PERM-CAT-1: source/seed guards. OpenAPI is checked on the generated document in OpenApiExportIT.
 */
class PermissionCatalogueTest {
  private static final Map<PermissionKey, String> UNGRANTED_EXCEPTIONS =
      Map.of(
          PermissionKey.FLOWBOARD_MANAGE,
          "Temporary legacy route gate; FE-ARCH-3 owns the fix. Do not seed it just to satisfy this test.",
          PermissionKey.ADMIN_ACCESS,
          "Admin uses the existing role-based mechanism. Changing its grant model is outside PERM-CAT-1.");

  private static PermissionSourceScanner.Result scan;
  private static List<PermissionTemplate> grants;

  @BeforeAll
  static void readSourcesAndBuildDesiredGrants() throws IOException {
    scan = PermissionSourceScanner.scanDirectory(Path.of("src/main/java"));
    // Pure construction: no seed(), database, Spring context or tenant writes.
    grants = new PermissionTemplateSeeder(null, null).buildDesiredTemplates();
  }

  @Test
  void everyDeclarativePairIsInTheCatalogue() {
    assertThat(scan.sites())
        .anyMatch(site -> site.kind() == PermissionSourceScanner.Kind.ANNOTATION);
    assertThat(
            unknownSites(
                scan.sites().stream()
                    .filter(site -> site.kind() == PermissionSourceScanner.Kind.ANNOTATION)
                    .toList()))
        .isEmpty();
  }

  @Test
  void everyJavaPermissionAndScopePairIsInTheCatalogue() {
    assertThat(scan.sites()).anyMatch(site -> site.kind() == PermissionSourceScanner.Kind.JAVA);
    assertThat(
            unknownSites(
                scan.sites().stream()
                    .filter(site -> site.kind() == PermissionSourceScanner.Kind.JAVA)
                    .toList()))
        .isEmpty();
  }

  @Test
  void reflectionPreventsNewPermissionApisEscapingTheScanner() {
    assertScannerCovers(
        DataScopeGuard.class, PermissionResult.class, SpELPermissionEvaluator.class);
  }

  @Test
  void enforcementClassificationMatchesCallSitesInBothDirections() {
    assertThat(enforcementMismatches(catalogue(), scan.sites())).isEmpty();
    assertThat(
            Arrays.stream(PermissionKey.values())
                .filter(
                    key ->
                        key.enforcedBy() == EnforcedBy.NONE
                            || key.enforcedBy() == EnforcedBy.FRONTEND_ROUTE))
        .allSatisfy(key -> assertThat(key.note()).as(key.key()).isNotBlank());
  }

  @Test
  void everyEnforcedPairHasADefaultGrantExceptTheTwoDocumentedRoutes() {
    assertThat(UNGRANTED_EXCEPTIONS).hasSize(2);
    assertThat(UNGRANTED_EXCEPTIONS.values()).allSatisfy(reason -> assertThat(reason).isNotBlank());
    assertThat(missingGrants(catalogue(), grants)).isEmpty();
    // Exceptions must not outlive the condition they document or silently turn into new grants.
    assertThat(grantKeys(grants))
        .doesNotContain(PermissionKey.FLOWBOARD_MANAGE.key(), PermissionKey.ADMIN_ACCESS.key());
  }

  @Test
  void everyProducedPairIsCataloguedAndWritesDerivedEvidence() throws IOException {
    assertThat(unknownGrants(grants)).isEmpty();
    Path output = Path.of("target/granted-pairs.txt");
    Files.createDirectories(output.getParent());
    Files.write(output, new TreeSet<>(grantKeys(grants)));
  }

  @Test
  void noParallelResourceOrActionSetIsDeclared() {
    // A helper only, not proof that no differently-shaped vocabulary exists.
    assertThat(scan.parallelVocabularies()).isEmpty();
  }

  @Test
  void wireValuesAndEveryLiteralAreCanonicalAndUnique() {
    assertThat(Arrays.stream(PermissionKey.values()).map(PermissionKey::key).toList())
        .doesNotHaveDuplicates();
    for (PermissionKey key : PermissionKey.values()) {
      assertThat(key.key()).matches("[a-z][a-z-]*:[a-z][a-z-]*");
      assertThat(key.name())
          .isEqualTo(key.key().toUpperCase(Locale.ROOT).replace(':', '_').replace('-', '_'));
      assertThat(PermissionKey.of(key.resource(), key.action())).contains(key);
    }
    assertThat(nonCanonicalSites(scan.sites())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "perms.can(\"widget\", \"read\")",
        "perms.scopeOf(\"widget\", \"read\")",
        "scope.assertCanAccess(\"procurement\", \"peek\", po)",
        "scope.canAccess(\"procurement\", \"peek\", po)",
        "scope.currentScope(\"procurement\", \"peek\")",
        "scope.scopeFilter(\"procurement\", \"peek\")",
        "scope.<PurchaseOrder>scopeFilter(\"procurement\", \"peek\")",
        "auth.can(authentication, \"widget\", \"read\")",
        "auth.hasScope(authentication, \"widget\", \"read\", \"OWN\")"
      })
  void redProbeUnknownJavaPairIncludingGenericScopeFilter(String expression) throws IOException {
    var result = fixture("class Probe { void run() { " + expression + "; } }");
    assertThat(result.sites()).hasSize(1);
    assertThatThrownBy(() -> assertThat(unknownSites(result.sites())).isEmpty())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Probe.java");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "can(authentication, 'sales', 'teleport')",
        "hasScope(authentication, 'sales', 'teleport', 'OWN')"
      })
  void redProbeUnknownSpelPair(String expression) throws IOException {
    var result =
        fixture("class Probe { @PreAuthorize(\"@auth." + expression + "\") void run() {} }");
    assertThat(result.sites()).hasSize(1);
    assertThat(unknownSites(result.sites()))
        .anySatisfy(error -> assertThat(error).contains("Probe.java", "sales:teleport"));
  }

  @Test
  void commentsDoNotCountButMultilineConcatenatedAnnotationsDo() throws IOException {
    var result =
        fixture(
            """
        class Probe {
          // perms.can("widget", "read");
          /** @PreAuthorize("@auth.can(authentication, 'SALES', 'WRITE')") */
          String example = "@auth.can(authentication, 'widget', 'read')";
          @PreAuthorize("@auth.can(authentication, " +
              "'sales', 'read')")
          void run() {}
        }
        """);
    assertThat(result.sites())
        .extracting(PermissionSourceScanner.Site::key)
        .containsExactly("sales:read");
  }

  @Test
  void redProbeCaseMismatchNamesTheSiteAndCanonicalPair() throws IOException {
    var result =
        fixture(
            "class Probe { @PreAuthorize(\"@auth.can(authentication, 'Sales', 'read')\") void run() {} }");
    assertThat(nonCanonicalSites(result.sites()))
        .anySatisfy(error -> assertThat(error).contains("Probe.java", "Sales:read", "sales:read"));
  }

  @Test
  void redProbeLivePairCannotBeMarkedNone() {
    var changed = new HashMap<>(catalogue());
    changed.put(PermissionKey.MEMBERS_MANAGE.key(), EnforcedBy.NONE);
    assertThat(enforcementMismatches(changed, scan.sites()))
        .anySatisfy(error -> assertThat(error).contains("members:manage"));
  }

  @Test
  void redProbeOrphanedConstantFailsBothSiteAndGrantChecks() {
    var changed = new HashMap<>(catalogue());
    changed.put("widget:read", EnforcedBy.ANNOTATION);
    assertThat(enforcementMismatches(changed, scan.sites()))
        .anySatisfy(error -> assertThat(error).contains("widget:read"));
    assertThat(missingGrants(changed, grants)).contains("widget:read");
  }

  @Test
  void redProbeRemovingAllQualityApproveGrantsReproducesTheOriginalDefect() {
    var changed =
        grants.stream().filter(grant -> !grantKey(grant).equals("quality:approve")).toList();
    assertThat(missingGrants(catalogue(), changed)).containsExactly("quality:approve");
  }

  @Test
  void redProbeRawStringSeedRegressionIsRejected() {
    var changed = new ArrayList<>(grants);
    changed.add(
        PermissionTemplate.builder()
            .roleCode("WORKER")
            .resource("widget")
            .action("read")
            .dataScope(DataScope.OWN)
            .build());
    assertThat(unknownGrants(changed)).containsExactly("widget:read");
  }

  @Test
  void redProbeReinventingTheRegistryIsRejected() throws IOException {
    var result =
        fixture(
            "class Probe { static final java.util.Set<String> VALID_RESOURCES = java.util.Set.of(\"widget\"); }");
    assertThat(result.parallelVocabularies())
        .anySatisfy(error -> assertThat(error).contains("Probe.java", "VALID_RESOURCES"));
  }

  @Test
  void redProbeKnownNameOverloadBreaksTheReflectionGuard() {
    assertThatThrownBy(() -> assertScannerCovers(PermissionResultOverloadProbe.class))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("PermissionResultOverloadProbe.can");
  }

  @Test
  void redProbeIndistinguishableOverloadOffsetsFailClosed() {
    List<PermissionSourceScanner.ApiDescriptor> ambiguous =
        new ArrayList<>(PermissionSourceScanner.apiDescriptors());
    ambiguous.add(
        PermissionSourceScanner.descriptor(
            PermissionResultOverloadProbe.class,
            "can",
            0,
            String.class,
            String.class,
            Object.class));
    assertThatThrownBy(
            () ->
                PermissionSourceScanner.unscannedApis(
                    ambiguous, PermissionResultOverloadProbe.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Indistinguishable permission API overloads", "can/3", "[0, 1]");
  }

  static final class PermissionResultOverloadProbe {
    public boolean can(String resource, String action, Object target) {
      return false;
    }
  }

  private static void assertScannerCovers(Class<?>... types) {
    assertThat(PermissionSourceScanner.unscannedApis(types))
        .as("Every public permission/scope API must be scanned")
        .isEmpty();
  }

  private static PermissionSourceScanner.Result fixture(String source) throws IOException {
    return PermissionSourceScanner.scan(Map.of("Probe.java", source));
  }

  private static Map<String, EnforcedBy> catalogue() {
    return Arrays.stream(PermissionKey.values())
        .collect(Collectors.toMap(PermissionKey::key, PermissionKey::enforcedBy));
  }

  private static List<String> unknownSites(List<PermissionSourceScanner.Site> sites) {
    return sites.stream()
        .filter(site -> PermissionKey.of(site.resource(), site.action()).isEmpty())
        .map(site -> site.location() + " unknown permission " + site.key())
        .toList();
  }

  private static List<String> enforcementMismatches(
      Map<String, EnforcedBy> catalogue, List<PermissionSourceScanner.Site> sites) {
    Map<String, EnforcedBy> observed = new HashMap<>();
    sites.forEach(
        site ->
            observed.merge(
                site.key(),
                site.kind() == PermissionSourceScanner.Kind.ANNOTATION
                    ? EnforcedBy.ANNOTATION
                    : EnforcedBy.JAVA,
                (first, second) ->
                    first == EnforcedBy.ANNOTATION || second == EnforcedBy.ANNOTATION
                        ? EnforcedBy.ANNOTATION
                        : EnforcedBy.JAVA));
    List<String> failures = new ArrayList<>();
    observed.forEach(
        (key, kind) -> {
          if (catalogue.get(key) != kind)
            failures.add(key + " has " + kind + " sites but catalogue says " + catalogue.get(key));
        });
    catalogue.forEach(
        (key, kind) -> {
          if ((kind == EnforcedBy.ANNOTATION || kind == EnforcedBy.JAVA)
              && !observed.containsKey(key))
            failures.add(key + " has no call site; add enforcement or document NONE");
        });
    return failures;
  }

  private static Set<String> missingGrants(
      Map<String, EnforcedBy> catalogue, List<PermissionTemplate> rows) {
    Set<String> granted = grantKeys(rows);
    Set<String> exceptions =
        UNGRANTED_EXCEPTIONS.keySet().stream().map(PermissionKey::key).collect(Collectors.toSet());
    return catalogue.entrySet().stream()
        .filter(entry -> entry.getValue() != EnforcedBy.NONE)
        .map(Map.Entry::getKey)
        .filter(key -> !granted.contains(key) && !exceptions.contains(key))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static List<String> unknownGrants(List<PermissionTemplate> rows) {
    return rows.stream()
        .filter(row -> PermissionKey.of(row.getResource(), row.getAction()).isEmpty())
        .map(PermissionCatalogueTest::grantKey)
        .distinct()
        .sorted()
        .toList();
  }

  private static List<String> nonCanonicalSites(List<PermissionSourceScanner.Site> sites) {
    Set<String> keys = catalogue().keySet();
    return sites.stream()
        .filter(
            site ->
                !keys.contains(site.key()) && keys.contains(site.key().toLowerCase(Locale.ROOT)))
        .map(
            site ->
                site.location()
                    + " "
                    + site.key()
                    + " must be "
                    + site.key().toLowerCase(Locale.ROOT))
        .toList();
  }

  private static String grantKey(PermissionTemplate grant) {
    return grant.getResource() + ":" + grant.getAction();
  }

  private static Set<String> grantKeys(List<PermissionTemplate> rows) {
    return rows.stream().map(PermissionCatalogueTest::grantKey).collect(Collectors.toSet());
  }
}
