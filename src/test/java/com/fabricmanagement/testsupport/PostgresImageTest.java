package com.fabricmanagement.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostgresImageTest {

  @Test
  @SuppressWarnings("resource")
  void digestPinnedImageIsAcceptedByPostgresqlContainer() {
    var container = PostgresImage.container();

    assertThat(container.getDockerImageName()).isEqualTo(System.getProperty("postgres.image"));
  }
}
