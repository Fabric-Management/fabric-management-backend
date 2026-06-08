package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
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
    assertThat(generatedSpec).contains("openapi: 3.0");

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
              "OpenAPI contract has drifted! Run 'mvn test -DUPDATE_OPENAPI=true -Dtest=OpenApiExportIT' to accept changes and commit.")
          .isEqualTo(existingSpec);
    }
  }
}
