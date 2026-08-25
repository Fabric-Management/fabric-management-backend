package com.fabricmanagement.common.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class HypersistenceObjectMapperConfiguration {

  private static final String OBJECT_MAPPER_PROPERTY = "hypersistence.utils.jackson.object.mapper";

  @Bean
  HibernatePropertiesCustomizer hypersistenceObjectMapperCustomizer(ObjectMapper objectMapper) {
    SpringObjectMapperSupplier.initialize(objectMapper);
    return properties ->
        properties.put(OBJECT_MAPPER_PROPERTY, SpringObjectMapperSupplier.class.getName());
  }
}
