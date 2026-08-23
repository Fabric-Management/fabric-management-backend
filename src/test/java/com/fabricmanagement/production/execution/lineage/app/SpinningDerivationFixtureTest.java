package com.fabricmanagement.production.execution.lineage.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.lineage.domain.rule.AttributeInheritanceSchema;
import com.fabricmanagement.production.execution.lineage.domain.rule.BatchAttributes;
import com.fabricmanagement.production.execution.lineage.domain.rule.InheritanceAction;
import com.fabricmanagement.production.execution.lineage.infra.configuration.AttributeInheritanceSchemaLoader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class SpinningDerivationFixtureTest {

  private static final String SCHEMA_LOCATION = "inheritance-rules/fiber-to-yarn.json";
  private static final String FIXTURE_LOCATION = "classpath*:derivations/spinning/*.json";

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final PathMatchingResourcePatternResolver resourceResolver =
      new PathMatchingResourcePatternResolver();

  private AttributeInheritanceSchema schema;
  private BatchAttributeInheritanceEngine engine;

  @BeforeEach
  void setUp() throws IOException {
    schema = readSchema();
    AttributeInheritanceSchemaLoader schemaLoader = mock(AttributeInheritanceSchemaLoader.class);
    when(schemaLoader.getSchema(schema.sourceType(), schema.targetType()))
        .thenReturn(Optional.of(schema));
    engine = new BatchAttributeInheritanceEngine(schemaLoader);
  }

  @Test
  void fixturesMatchExistingBatchAttributeInheritanceEngine() throws IOException {
    for (Resource resource : fixtureResources()) {
      SpinningFixture fixture = readFixture(resource);
      List<BatchAttributes> parents =
          fixture.parents().stream()
              .map(parent -> new BatchAttributes(parent.attributes(), parent.quantity()))
              .toList();

      if (fixture.expectsError()) {
        assertThrows(
            BatchDomainException.class,
            () ->
                engine.resolveInheritedAttributes(
                    parents, schema.sourceType(), schema.targetType()),
            fixture.description());
        continue;
      }

      Map<String, Object> actual =
          engine.resolveInheritedAttributes(parents, schema.sourceType(), schema.targetType());
      assertCapturedMapEquals(fixture.expected(), actual, fixture.description());
    }
  }

  @Test
  void everyClasspathRuleHasSuccessfulFixtureCoverage() throws IOException {
    List<SpinningFixture> successfulFixtures =
        Arrays.stream(fixtureResources())
            .map(this::readFixtureUnchecked)
            .filter(fixture -> !fixture.expectsError())
            .toList();

    Set<RuleCoverage> requiredRules = new LinkedHashSet<>();
    schema.rules().stream()
        .map(rule -> new RuleCoverage(rule.sourceAttribute(), rule.action()))
        .forEach(requiredRules::add);

    Set<RuleCoverage> representedRules = new LinkedHashSet<>();
    for (RuleCoverage rule : requiredRules) {
      boolean represented =
          successfulFixtures.stream()
              .flatMap(fixture -> fixture.parents().stream())
              .anyMatch(parent -> parent.attributes().containsKey(rule.sourceAttribute()));
      if (represented) {
        representedRules.add(rule);
      }
    }

    Set<RuleCoverage> missingRules = new LinkedHashSet<>(requiredRules);
    missingRules.removeAll(representedRules);
    assertTrue(
        missingRules.isEmpty(),
        () -> "Missing successful spinning fixture coverage for classpath rules: " + missingRules);
  }

  private AttributeInheritanceSchema readSchema() throws IOException {
    Resource resource = new ClassPathResource(SCHEMA_LOCATION);
    assertTrue(resource.exists(), "Classpath spinning inheritance schema must exist");
    try (var input = resource.getInputStream()) {
      return objectMapper.readValue(input, AttributeInheritanceSchema.class);
    }
  }

  private Resource[] fixtureResources() throws IOException {
    Resource[] resources = resourceResolver.getResources(FIXTURE_LOCATION);
    Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
    assertTrue(resources.length > 0, "At least one spinning derivation fixture must exist");
    return resources;
  }

  private SpinningFixture readFixture(Resource resource) throws IOException {
    try (var input = resource.getInputStream()) {
      return objectMapper.readValue(input, SpinningFixture.class);
    }
  }

  private SpinningFixture readFixtureUnchecked(Resource resource) {
    try {
      return readFixture(resource);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Cannot read spinning fixture " + resource.getFilename(), exception);
    }
  }

  private static void assertCapturedMapEquals(
      Map<String, Object> expected, Map<String, Object> actual, String description) {
    assertEquals(expected.keySet(), actual.keySet(), description + " — target keys");
    for (Map.Entry<String, Object> entry : expected.entrySet()) {
      assertCapturedValueEquals(
          entry.getValue(), actual.get(entry.getKey()), description + " — " + entry.getKey());
    }
  }

  private static void assertCapturedValueEquals(Object expected, Object actual, String path) {
    if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
      BigDecimal expectedDecimal = new BigDecimal(expectedNumber.toString());
      BigDecimal actualDecimal = new BigDecimal(actualNumber.toString());
      assertEquals(0, expectedDecimal.compareTo(actualDecimal), path);
      return;
    }

    if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
      assertEquals(expectedList.size(), actualList.size(), path + " — list size");
      for (int index = 0; index < expectedList.size(); index++) {
        assertCapturedValueEquals(
            expectedList.get(index), actualList.get(index), path + "[" + index + "]");
      }
      return;
    }

    assertEquals(expected, actual, path);
  }

  private record SpinningFixture(
      String description,
      List<ParentFixture> parents,
      Map<String, Object> expected,
      Boolean expectError) {

    private SpinningFixture {
      parents = parents == null ? List.of() : List.copyOf(parents);
      expected = expected == null ? Map.of() : Map.copyOf(expected);
    }

    private boolean expectsError() {
      return Boolean.TRUE.equals(expectError);
    }
  }

  private record ParentFixture(Map<String, Object> attributes, BigDecimal quantity) {

    private ParentFixture {
      attributes = attributes == null ? Map.of() : new LinkedHashMap<>(attributes);
    }
  }

  private record RuleCoverage(String sourceAttribute, InheritanceAction action) {}
}
