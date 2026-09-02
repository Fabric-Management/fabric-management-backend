package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.ClassUtils;

/**
 * Guards the repository-owned assumptions behind the temporary SEC-SPRING-1 suppressions.
 *
 * <p>The classpath and source checks describe what this codebase depends on. The YAML checks
 * describe what its committed profiles request; external configuration can still override those
 * values at runtime. These guards therefore force a new review when the repository changes, but do
 * not claim that deployment-time configuration can never enable an affected feature.
 */
class DependencySecurityAssumptionsTest {

  private static final String APPLICATION_PACKAGE = "com.fabricmanagement..";
  private static final Path APPLICATION_RESOURCES = Path.of("src", "main", "resources");
  private static final JavaClasses APPLICATION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.fabricmanagement");

  @Test
  void webFluxMustRemainOffTheClasspath() {
    assertThat(
            ClassUtils.isPresent(
                "org.springframework.web.reactive.function.server.RouterFunction",
                getClass().getClassLoader()))
        .as(
            "CVE-2026-47892/CVE-2026-47893: Spring WebFlux must remain absent while these"
                + " version-pinned suppressions are active")
        .isFalse();
  }

  @Test
  void mvcFunctionalEndpointsMustRemainUnused() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(APPLICATION_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework.web.servlet.function..")
            .as(
                "CVE-2026-59313: Spring MVC functional endpoints are forbidden while the"
                    + " spring-core 6.2.19 suppression is active");

    rule.check(APPLICATION_CLASSES);
  }

  @Test
  void simpleEvaluationContextAndSpelCompilerMustRemainUnused() throws IOException {
    assertNoProductionDependency(
        "org.springframework.expression.spel.support.SimpleEvaluationContext",
        "CVE-2026-59283: SimpleEvaluationContext requires a new vulnerability assessment");
    assertNoProductionDependency(
        "org.springframework.expression.spel.SpelParserConfiguration",
        "CVE-2026-59283: SpelParserConfiguration requires a new vulnerability assessment");

    List<YamlProperty> properties = loadApplicationYamlProperties();
    assertThat(properties)
        .as("CVE-2026-59283: application YAML profiles must be discoverable")
        .isNotEmpty();
    assertThat(properties)
        .as(
            "CVE-2026-59283: spring.expression.compiler.mode must remain absent from committed"
                + " application profiles")
        .noneMatch(
            property -> isPropertyOrDescendant(property.name(), "spring.expression.compiler.mode"));
  }

  @Test
  void embeddedUnboundIdLdapMustRemainUnused() throws IOException {
    assertThat(
            ClassUtils.isPresent(
                "com.unboundid.ldap.listener.InMemoryDirectoryServer", getClass().getClassLoader()))
        .as(
            "CVE-2026-59270: embedded UnboundID LDAP must remain absent while the"
                + " spring-security-core 6.5.11 suppression is active")
        .isFalse();

    List<YamlProperty> properties = loadApplicationYamlProperties();
    assertThat(properties)
        .as("CVE-2026-59270: application YAML profiles must be discoverable")
        .isNotEmpty();
    assertThat(properties)
        .as(
            "CVE-2026-59270: spring.ldap.embedded configuration must remain absent from"
                + " committed application profiles")
        .noneMatch(property -> isPropertyOrDescendant(property.name(), "spring.ldap.embedded"));
  }

  @Test
  void actuatorEnvironmentEndpointMustRemainUnexposed() throws IOException {
    List<YamlProperty> exposureProperties =
        loadApplicationYamlProperties().stream()
            .filter(
                property ->
                    isPropertyOrIndexedValue(
                        property.name(), "management.endpoints.web.exposure.include"))
            .toList();

    assertThat(exposureProperties)
        .as(
            "CVE-2026-59284: at least one committed profile must declare the actuator exposure"
                + " allow-list")
        .isNotEmpty();

    List<String> exposedEndpoints =
        exposureProperties.stream()
            .flatMap(property -> commaSeparatedValues(property.value()))
            .toList();
    assertThat(exposedEndpoints)
        .as(
            "CVE-2026-59284: committed actuator exposure must contain neither env nor wildcard"
                + " while the Spring Cloud 4.3.3 suppression is active")
        .noneMatch(endpoint -> endpoint.equalsIgnoreCase("env") || endpoint.equals("*"));
  }

  private static void assertNoProductionDependency(String className, String reason) {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(APPLICATION_PACKAGE)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(className)
            .as(reason);

    rule.check(APPLICATION_CLASSES);
  }

  private static List<YamlProperty> loadApplicationYamlProperties() throws IOException {
    List<Path> yamlFiles;
    try (Stream<Path> files = Files.list(APPLICATION_RESOURCES)) {
      yamlFiles =
          files
              .filter(Files::isRegularFile)
              .filter(
                  path ->
                      path.getFileName().toString().startsWith("application")
                          && path.getFileName().toString().endsWith(".yml"))
              .sorted()
              .toList();
    }

    assertThat(yamlFiles)
        .as("SEC-SPRING-1: application*.yml profiles must be discovered dynamically")
        .isNotEmpty();

    return yamlFiles.stream().flatMap(DependencySecurityAssumptionsTest::loadYaml).toList();
  }

  private static Stream<YamlProperty> loadYaml(Path yamlFile) {
    try {
      return new YamlPropertySourceLoader()
          .load(yamlFile.getFileName().toString(), new FileSystemResource(yamlFile)).stream()
              .filter(EnumerablePropertySource.class::isInstance)
              .map(propertySource -> (EnumerablePropertySource<?>) propertySource)
              .flatMap(
                  propertySource ->
                      Arrays.stream(propertySource.getPropertyNames())
                          .map(
                              name ->
                                  new YamlProperty(
                                      yamlFile, name, propertySource.getProperty(name))));
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read application profile " + yamlFile, exception);
    }
  }

  private static boolean isPropertyOrDescendant(String candidate, String propertyName) {
    return candidate.equals(propertyName)
        || candidate.startsWith(propertyName + ".")
        || candidate.startsWith(propertyName + "[");
  }

  private static boolean isPropertyOrIndexedValue(String candidate, String propertyName) {
    return candidate.equals(propertyName) || candidate.startsWith(propertyName + "[");
  }

  private static Stream<String> commaSeparatedValues(Object value) {
    return Arrays.stream(String.valueOf(value).split(","))
        .map(String::trim)
        .filter(token -> !token.isEmpty());
  }

  private record YamlProperty(Path file, String name, Object value) {}
}
