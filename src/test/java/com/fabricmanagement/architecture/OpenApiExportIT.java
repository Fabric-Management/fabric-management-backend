package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
public class OpenApiExportIT {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void exportAndVerifyOpenApiSpec() throws Exception {
    // 1. Generate fresh spec from running app
    ResponseEntity<String> response = restTemplate.getForEntity("/api-docs.yaml", String.class);

    // Assert successful generation and valid OpenAPI signature
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String generatedSpec = response.getBody();
    assertThat(generatedSpec).isNotNull();
    assertThat(generatedSpec).contains("openapi: 3.");
    assertThat(generatedSpec).doesNotContainPattern("(?m)^    [A-Za-z][A-Za-z0-9]*_1:$");

    Map<String, Object> generatedDocument =
        new YAMLMapper().readValue(generatedSpec, new TypeReference<Map<String, Object>>() {});
    assertValidationContracts(generatedDocument);

    // 2. Define the target spec file location
    File specFile = new File("api/openapi.yaml");

    // 3. Determine if we are in update mode
    boolean updateMode =
        Boolean.parseBoolean(System.getProperty("UPDATE_OPENAPI", "false"))
            || Boolean.parseBoolean(System.getenv("UPDATE_OPENAPI"));

    // 4. Update or Validate
    if (!specFile.exists() || updateMode) {
      specFile.getParentFile().mkdirs();
      Files.writeString(specFile.toPath(), generatedSpec);
      System.out.println("✅ OpenAPI spec written to " + specFile.getAbsolutePath());
    } else {
      String existingSpec = Files.readString(specFile.toPath());

      assertThat(generatedSpec)
          .as(
              "OpenAPI contract has drifted! Run 'UPDATE_OPENAPI=true ./mvnw verify -Dit.test=OpenApiExportIT' to accept changes and commit.")
          .isEqualTo(existingSpec);
    }
  }

  private void assertValidationContracts(Map<String, Object> document) {
    assertThat(document.get("openapi")).isEqualTo("3.1.0");

    assertPercentageBounds(document, "CreateFiberQualityStandardRequest", "elongationPctMin");
    assertPercentageBounds(document, "UpdateFiberQualityStandardRequest", "moisturePctTarget");
    assertPercentageBounds(document, "CreateFiberTestResultRequest", "trashContentPercent");
    assertNoInvalidRangeMaximum(document, "openapi");

    Map<String, Object> positive = schemaProperty(document, "AddOutputItemRequest", "netWeight");
    assertNumericValue(positive, "exclusiveMinimum", BigDecimal.ZERO);
    assertThat(positive).doesNotContainKey("minimum");

    Map<String, Object> positiveOrZero =
        schemaProperty(document, "CreateFiberQualityStandardRequest", "finenessMin");
    assertNumericValue(positiveOrZero, "minimum", BigDecimal.ZERO);
    assertThat(positiveOrZero).doesNotContainKey("exclusiveMinimum");

    assertThat(schemaProperty(document, "CreateExternalUserRequest", "department"))
        .containsEntry("deprecated", true);
    assertThat(schemaProperty(document, "CreateInternalUserRequest", "department"))
        .doesNotContainKey("deprecated");
    assertThat(schemaProperty(document, "AssignContactRequest", "department"))
        .doesNotContainKey("deprecated");
  }

  private void assertPercentageBounds(
      Map<String, Object> document, String schemaName, String propertyName) {
    Map<String, Object> percentage = schemaProperty(document, schemaName, propertyName);
    assertNumericValue(percentage, "minimum", BigDecimal.ZERO);
    assertNumericValue(percentage, "maximum", BigDecimal.valueOf(100));
  }

  private void assertNumericValue(Map<String, Object> schema, String keyword, BigDecimal expected) {
    Object actual = schema.get(keyword);
    assertThat(actual).as("OpenAPI keyword '%s'", keyword).isInstanceOf(Number.class);
    assertThat(new BigDecimal(actual.toString())).isEqualByComparingTo(expected);
  }

  private void assertNoInvalidRangeMaximum(Object node, String path) {
    if (node instanceof Map<?, ?> map) {
      if (map.containsKey("maximum")) {
        assertThat(String.valueOf(map.get("maximum")))
            .as("maximum at %s", path)
            .isNotEqualTo(String.valueOf(Long.MAX_VALUE));
      }
      map.forEach((key, value) -> assertNoInvalidRangeMaximum(value, path + "." + key));
    } else if (node instanceof Iterable<?> iterable) {
      int index = 0;
      for (Object value : iterable) {
        assertNoInvalidRangeMaximum(value, path + "[" + index + "]");
        index++;
      }
    }
  }

  private Map<String, Object> schemaProperty(
      Map<String, Object> document, String schemaName, String propertyName) {
    return mapAt(document, "components", "schemas", schemaName, "properties", propertyName);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapAt(Map<String, Object> root, String... path) {
    Object current = root;
    StringBuilder traversed = new StringBuilder();

    for (String segment : path) {
      assertThat(current)
          .as("OpenAPI map at %s", traversed.isEmpty() ? "root" : traversed)
          .isInstanceOf(Map.class);
      current = ((Map<?, ?>) current).get(segment);
      traversed.append('.').append(segment);
      assertThat(current).as("OpenAPI value at %s", traversed).isNotNull();
    }

    assertThat(current).as("OpenAPI map at %s", traversed).isInstanceOf(Map.class);
    return (Map<String, Object>) current;
  }
}
