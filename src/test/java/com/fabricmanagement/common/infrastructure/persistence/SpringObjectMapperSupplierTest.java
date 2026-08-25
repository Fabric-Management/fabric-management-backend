package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpringObjectMapperSupplierTest {

  private static final String OBJECT_MAPPER_PROPERTY = "hypersistence.utils.jackson.object.mapper";

  @Test
  void hibernateConfigurationSettingsCanSelectTheSpringSupplier() {
    ObjectMapper previousMapper =
        JsonConfiguration.INSTANCE.getObjectMapperWrapper().getObjectMapper();
    ObjectMapper springMapper = new ObjectMapper();

    try {
      SpringObjectMapperSupplier.initialize(springMapper);

      JsonConfiguration configuration =
          new JsonConfiguration(
              Map.of(OBJECT_MAPPER_PROPERTY, SpringObjectMapperSupplier.class.getName()));

      assertThat(configuration.getObjectMapperWrapper().getObjectMapper()).isSameAs(springMapper);
    } finally {
      SpringObjectMapperSupplier.initialize(previousMapper);
    }
  }

  @Test
  void initializeAlsoUpdatesTheGlobalHypersistenceWrapperAsFallback() {
    ObjectMapper previousMapper =
        JsonConfiguration.INSTANCE.getObjectMapperWrapper().getObjectMapper();
    ObjectMapper springMapper = new ObjectMapper();

    try {
      SpringObjectMapperSupplier.initialize(springMapper);

      assertThat(new SpringObjectMapperSupplier().get()).isSameAs(springMapper);
      assertThat(JsonConfiguration.INSTANCE.getObjectMapperWrapper().getObjectMapper())
          .isSameAs(springMapper);
    } finally {
      SpringObjectMapperSupplier.initialize(previousMapper);
    }
  }
}
