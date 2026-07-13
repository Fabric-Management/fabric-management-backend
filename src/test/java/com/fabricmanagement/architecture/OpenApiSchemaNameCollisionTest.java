package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Protects component schema names before springdoc can collapse two Java DTO types into one.
 *
 * <p>This guard intentionally follows the codebase convention that API types live in {@code dto}
 * packages. It includes public top-level and nested types in those packages, but it does not
 * attempt to discover controller reachability or API types outside the convention. Lombok-generated
 * builder implementation classes are excluded because they are not DTO declarations or schema
 * candidates.
 */
class OpenApiSchemaNameCollisionTest {

  private static final Pattern DTO_PACKAGE = Pattern.compile("(^|\\.)dto($|\\.)");
  private static final Pattern SPRINGDOC_SUFFIXED_SCHEMA =
      Pattern.compile("(?m)^    [A-Za-z][A-Za-z0-9]*_1:$");
  private static final Pattern COMPONENT_SCHEMA =
      Pattern.compile("(?m)^    ([A-Za-z][A-Za-z0-9._-]*):(?:\\s.*)?$");
  private static final Pattern COMPONENT_SCHEMA_REF =
      Pattern.compile("#/components/schemas/([A-Za-z][A-Za-z0-9._-]*)");

  private static final Map<String, Set<String>> ALLOWED_COLLISIONS = Map.of();

  @Test
  void publicDtoTypesShouldHaveUniqueEffectiveSchemaNamesExceptForExplicitAllowlist() {
    Map<String, Set<String>> actualCollisions =
        new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.fabricmanagement")
                .stream()
                .filter(javaClass -> DTO_PACKAGE.matcher(javaClass.getPackageName()).find())
                .filter(javaClass -> javaClass.getModifiers().contains(JavaModifier.PUBLIC))
                .filter(javaClass -> !sourceSimpleName(javaClass).endsWith("Builder"))
                .collect(
                    Collectors.groupingBy(
                        OpenApiSchemaNameCollisionTest::effectiveSchemaName,
                        TreeMap::new,
                        Collectors.mapping(
                            JavaClass::getName, Collectors.toCollection(TreeSet::new))))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, duplicate) -> first,
                        TreeMap::new));

    assertThat(actualCollisions)
        .as(
            "Public DTO types must have unique effective OpenAPI schema names. "
                + "Rename collisions with @Schema(name = ...); new collisions are not allowlisted. "
                + "Actual collisions name every Java class.")
        .isEqualTo(ALLOWED_COLLISIONS);
  }

  @Test
  void committedOpenApiShouldNotContainSpringdocNumericSchemaSuffixes() throws IOException {
    String openApi = Files.readString(Path.of("api/openapi.yaml"));

    assertThat(openApi).doesNotContainPattern(SPRINGDOC_SUFFIXED_SCHEMA);
  }

  @Test
  void committedOpenApiShouldNotContainDanglingComponentSchemaReferences() throws IOException {
    String openApi = Files.readString(Path.of("api/openapi.yaml"));
    String componentsMarker = "components:\n  schemas:\n";
    int componentsStart = openApi.indexOf(componentsMarker);
    assertThat(componentsStart)
        .as("OpenAPI must define components.schemas")
        .isGreaterThanOrEqualTo(0);
    String schemasSection = openApi.substring(componentsStart + componentsMarker.length());

    Set<String> definedSchemas =
        COMPONENT_SCHEMA
            .matcher(schemasSection)
            .results()
            .map(match -> match.group(1))
            .collect(Collectors.toSet());
    Set<String> referencedSchemas =
        COMPONENT_SCHEMA_REF
            .matcher(openApi)
            .results()
            .map(match -> match.group(1))
            .collect(Collectors.toSet());

    assertThat(definedSchemas)
        .as("Every OpenAPI component schema reference must resolve to a defined component")
        .containsAll(referencedSchemas);
  }

  private static String effectiveSchemaName(JavaClass javaClass) {
    if (!javaClass.isAnnotatedWith(Schema.class)) {
      return sourceSimpleName(javaClass);
    }

    String explicitName = javaClass.getAnnotationOfType(Schema.class).name();
    return explicitName.isBlank() ? sourceSimpleName(javaClass) : explicitName;
  }

  private static String sourceSimpleName(JavaClass javaClass) {
    String className = javaClass.getName();
    int separator = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
    return className.substring(separator + 1);
  }
}
