package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;

class ListenerIdUniquenessTest {

  @Test
  @DisplayName("No two @ApplicationModuleListener methods should produce the same listener_id")
  void listenerIdsMustBeUnique() {
    JavaClasses importedClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");

    Set<String> listenerIds = new HashSet<>();

    for (var clazz : importedClasses) {
      for (JavaMethod method : clazz.getMethods()) {
        if (method.isAnnotatedWith(ApplicationModuleListener.class)) {
          String listenerId = clazz.getSimpleName() + "#" + method.getName();
          boolean isNew = listenerIds.add(listenerId);
          assertThat(isNew).withFailMessage("Duplicate listener_id found: " + listenerId).isTrue();
        }
      }
    }
  }
}
