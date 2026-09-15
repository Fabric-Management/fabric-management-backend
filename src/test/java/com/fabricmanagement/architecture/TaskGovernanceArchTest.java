package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.fabricmanagement.flowboard.task.app.TaskProvisioningService;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.app.TaskTransitionOrchestrator;
import com.fabricmanagement.flowboard.task.app.TaskWorkflowRegistry;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TaskGovernanceArchTest {

  private static JavaClasses productionClasses;

  @BeforeAll
  static void importProductionClasses() {
    productionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");
  }

  @Test
  void onlyProvisioningServiceCreatesTaskAggregates() {
    ArchRule rule =
        noClasses()
            .that()
            .doNotHaveFullyQualifiedName(TaskProvisioningService.class.getName())
            .should()
            .callMethod(
                Task.class,
                "create",
                String.class,
                UUID.class,
                String.class,
                TaskType.class,
                ModuleType.class,
                Priority.class,
                LocalDate.class,
                BigDecimal.class,
                String.class,
                UUID.class)
            .as("TaskProvisioningService is the only production Task aggregate creator");

    rule.check(productionClasses);
  }

  @Test
  void governedStatusGuardUsesThePinnedWorkflowPredicate() {
    ArchRule rule =
        classes()
            .that()
            .haveFullyQualifiedName(TaskService.class.getName())
            .should()
            .callMethod(TaskWorkflowRegistry.class, "isGoverned", UUID.class, Integer.class)
            .as("legacy status mutations must consult the stored workflow pin");

    rule.check(productionClasses);
  }

  @Test
  void transitionResolutionReceivesTheStoredPinPair() {
    ArchRule resolvesStoredPin =
        classes()
            .that()
            .haveFullyQualifiedName(TaskTransitionOrchestrator.class.getName())
            .should()
            .callMethod(TaskWorkflowRegistry.class, "resolve", UUID.class, Integer.class)
            .as("Task transitions must resolve the immutable workflow pin");
    ArchRule doesNotResolveLatestByType =
        noClasses()
            .that()
            .haveFullyQualifiedName(TaskTransitionOrchestrator.class.getName())
            .should()
            .callMethod(TaskWorkflowRegistry.class, "latestFor", TaskType.class)
            .as("Task transitions must never replace a stored pin with the latest type default");

    resolvesStoredPin.check(productionClasses);
    doesNotResolveLatestByType.check(productionClasses);
  }
}
