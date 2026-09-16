package com.fabricmanagement.testsupport;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Provides the repository-governed PostgreSQL image to every Testcontainers caller. */
public final class PostgresImage {
  private static final String PROPERTY = "postgres.image";

  private PostgresImage() {}

  public static PostgreSQLContainer<?> container() {
    return new PostgreSQLContainer<>(name());
  }

  private static DockerImageName name() {
    String image = System.getProperty(PROPERTY);
    if (image == null || image.isBlank()) {
      throw new IllegalStateException(
          "Missing -Dpostgres.image. Run tests through Maven so pom.xml supplies the governed image.");
    }
    return DockerImageName.parse(image).asCompatibleSubstituteFor("postgres");
  }
}
