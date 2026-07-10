package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DomainEventJsonCreatorArchTest {

  private static final Set<String> REQUIRED_ENVELOPE_PROPERTIES =
      Set.of("eventId", "tenantId", "eventType", "occurredAt", "correlationId");

  @Test
  void concreteDomainEventsDeclareJsonCreatorEnvelopeConstructor() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");

    List<String> offenders =
        classes.stream()
            .filter(javaClass -> javaClass.isAssignableTo(DomainEvent.class))
            .filter(javaClass -> !javaClass.getName().equals(DomainEvent.class.getName()))
            .map(JavaClass::reflect)
            .filter(type -> !Modifier.isAbstract(type.getModifiers()))
            .filter(type -> !hasJsonCreatorEnvelopeConstructor(type))
            .map(Class::getName)
            .sorted()
            .toList();

    assertThat(offenders)
        .withFailMessage(
            "Concrete DomainEvent subclasses must declare a @JsonCreator constructor carrying "
                + "the envelope @JsonPropertys %s. Offenders: %s",
            REQUIRED_ENVELOPE_PROPERTIES, offenders)
        .isEmpty();
  }

  private static boolean hasJsonCreatorEnvelopeConstructor(Class<?> type) {
    return Arrays.stream(type.getDeclaredConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(JsonCreator.class))
        .anyMatch(DomainEventJsonCreatorArchTest::hasRequiredEnvelopeProperties);
  }

  private static boolean hasRequiredEnvelopeProperties(Constructor<?> constructor) {
    Set<String> jsonProperties =
        Arrays.stream(constructor.getParameterAnnotations())
            .flatMap(Arrays::stream)
            .filter(JsonProperty.class::isInstance)
            .map(JsonProperty.class::cast)
            .map(JsonProperty::value)
            .collect(java.util.stream.Collectors.toSet());

    return jsonProperties.containsAll(REQUIRED_ENVELOPE_PROPERTIES);
  }
}
