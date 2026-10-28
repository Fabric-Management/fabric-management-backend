package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.flowboard.collection.api.controller.CollectionFollowUpController;
import com.fabricmanagement.flowboard.task.app.TaskWorkflowRegistry;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CollectionFollowUpArchTest {
  @Test
  void workNeverPostsMoneyOrReadsForeignRepositories() {
    noClasses()
        .that()
        .resideInAPackage("..flowboard.collection..")
        .and()
        .resideOutsideOfPackage("..app.listener..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..finance..", "..sales..", "..platform.user.infra..")
        .check(
            new ClassFileImporter()
                .withImportOption(
                    com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.fabricmanagement.flowboard.collection"));
  }

  @Test
  void allEndpointsRequireFinanceReadAndTypedWorkflowCannotBeCreatedManually() {
    for (var method : CollectionFollowUpController.class.getDeclaredMethods()) {
      if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
      assertThat(method.getAnnotation(PreAuthorize.class).value())
          .contains("'finance', 'read'")
          .contains("'flowboard'");
    }
    var workflow = new TaskWorkflowRegistry();
    assertThat(workflow.allowsManualCreation(TaskType.COLLECTION_FOLLOW_UP)).isFalse();
    assertThat(workflow.isGoverned(TaskWorkflowRegistry.COLLECTION_DEFINITION_ID, 1)).isTrue();
  }
}
