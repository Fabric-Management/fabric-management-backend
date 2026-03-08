package com.fabricmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fails the build if two different Spring stereotype classes share the same default bean name.
 * Prevents {@code ConflictingBeanDefinitionException} (e.g. duplicate controllers after refactors).
 */
class DuplicateSpringBeanNameTest {

  private static final String BASE_PACKAGE = "com.fabricmanagement";

  @Test
  @DisplayName("No two stereotype classes may have the same default bean name")
  void noDuplicateDefaultBeanNames() {
    ClassPathScanningCandidateComponentProvider provider =
        new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    provider.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
    provider.addIncludeFilter(new AnnotationTypeFilter(Service.class));
    provider.addIncludeFilter(new AnnotationTypeFilter(Component.class));
    provider.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
    provider.addIncludeFilter(new AnnotationTypeFilter(Configuration.class));

    Set<BeanDefinition> definitions = provider.findCandidateComponents(BASE_PACKAGE);

    Map<String, List<String>> beanNameToClasses =
        definitions.stream()
            .collect(
                Collectors.groupingBy(
                    bd -> defaultBeanName(bd.getBeanClassName()),
                    Collectors.mapping(BeanDefinition::getBeanClassName, Collectors.toList())));

    List<String> errors = new ArrayList<>();
    beanNameToClasses.forEach(
        (beanName, classNames) -> {
          if (classNames.size() > 1) {
            errors.add("Duplicate bean name '" + beanName + "': " + String.join(", ", classNames));
          }
        });

    assertThat(errors)
        .as(
            "Duplicate Spring bean names would cause ConflictingBeanDefinitionException at startup. "
                + "Rename one class or use @RestController(\"uniqueName\") etc.")
        .isEmpty();
  }

  /** Same logic as Spring's default bean name: decapitalize short class name. */
  private static String defaultBeanName(String className) {
    if (className == null) return "";
    String shortName = className.substring(className.lastIndexOf('.') + 1);
    return Introspector.decapitalize(shortName);
  }
}
