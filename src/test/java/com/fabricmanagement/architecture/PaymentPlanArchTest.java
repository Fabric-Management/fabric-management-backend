package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.sales.salesorder.api.controller.SalesOrderPaymentPlanController;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PaymentPlanArchTest {
  @Test
  void paymentPlanNeverImportsFinanceOrFlowboardImplementations() {
    noClasses()
        .that()
        .resideInAPackage("..sales.salesorder.domain.paymentplan..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..finance..",
            "..flowboard..",
            "..sales.salesorder.app..",
            "..sales.salesorder.infra..")
        .check(
            new ClassFileImporter()
                .importPackages("com.fabricmanagement.sales.salesorder.domain.paymentplan"));
  }

  @Test
  void everyPlanEndpointHasTheActualSalesResourcePermission() {
    for (var method : SalesOrderPaymentPlanController.class.getDeclaredMethods()) {
      if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
      String action =
          java.util.Set.of("read", "history").contains(method.getName()) ? "read" : "write";
      assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
      assertThat(method.getAnnotation(PreAuthorize.class).value())
          .isEqualTo("@auth.can(authentication, 'sales', '" + action + "')");
    }
  }
}
