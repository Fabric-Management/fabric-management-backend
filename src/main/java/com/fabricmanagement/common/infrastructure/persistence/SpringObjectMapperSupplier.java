package com.fabricmanagement.common.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier;
import java.util.Objects;

/** Supplies Hypersistence JSON types with the ObjectMapper configured by Spring Boot. */
public final class SpringObjectMapperSupplier implements ObjectMapperSupplier {

  private static volatile ObjectMapper objectMapper;

  public SpringObjectMapperSupplier() {}

  static void initialize(ObjectMapper springObjectMapper) {
    ObjectMapper initializedMapper =
        Objects.requireNonNull(springObjectMapper, "springObjectMapper must not be null");
    objectMapper = initializedMapper;

    // Hibernate 6 passes the supplier property through TypeBootstrapContext. Keep the global
    // wrapper aligned as a lifecycle-safe fallback for an eagerly initialized JsonType.INSTANCE.
    JsonConfiguration.INSTANCE.getObjectMapperWrapper().setObjectMapper(initializedMapper);
  }

  @Override
  public ObjectMapper get() {
    return Objects.requireNonNull(
        objectMapper,
        "Spring ObjectMapper has not been initialized before Hypersistence JSON type access");
  }
}
