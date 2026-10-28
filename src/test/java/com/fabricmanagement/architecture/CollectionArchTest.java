package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.finance.collection.api.controller.CollectionController;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderPaymentPlanAgreedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderPaymentPlanCancelledEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderPaymentPlanRevisedEvent;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.access.prepost.PreAuthorize;

class CollectionArchTest {
  private static final String COLLECTION = "com.fabricmanagement.finance.collection";
  private static final String SALES = "com.fabricmanagement.sales";

  /** A sales type that is not published to other modules as a {@code @NamedInterface} package. */
  private static DescribedPredicate<JavaClass> unpublishedSalesTypes() {
    return DescribedPredicate.describe(
        "sales types outside a @NamedInterface package",
        type ->
            type.getPackageName().startsWith(SALES + ".")
                && type.reflect().getPackage().getAnnotation(NamedInterface.class) == null);
  }

  @Test
  void financeReadsSalesOnlyThroughItsOwnPortAndPublishedSalesEvents() {
    var collection =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(COLLECTION);
    // Outside the listener package the collection module knows nothing about sales at all.
    noClasses()
        .that()
        .resideInAPackage("..finance.collection..")
        .and()
        .resideOutsideOfPackage("..finance.collection.app.listener..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..sales..")
        .check(collection);
    // The listener package may react to sales events, and only to events that sales publishes as
    // a named interface; it can never reach a sales entity, repository or service.
    noClasses()
        .that()
        .resideInAPackage("..finance.collection..")
        .should()
        .dependOnClassesThat(unpublishedSalesTypes())
        .check(collection);
    noClasses()
        .that()
        .resideInAPackage("..finance.collection.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..app..", "..infra..", "..api..", "..sales..", "..flowboard..")
        .check(
            new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.fabricmanagement.finance.collection.domain"));
  }

  @Test
  void theSalesEventsCollectionReactsToArePublishedAsANamedInterface() {
    for (Class<?> event :
        List.of(
            SalesOrderPaymentPlanAgreedEvent.class,
            SalesOrderPaymentPlanRevisedEvent.class,
            SalesOrderPaymentPlanCancelledEvent.class)) {
      var published = event.getPackage().getAnnotation(NamedInterface.class);
      assertThat(published).as("%s package", event.getSimpleName()).isNotNull();
      assertThat(published.toString()).contains("events");
    }
  }

  @Test
  void allCollectionEndpointsUseTheirFinancePermissionAndNeverSalesAuthority() {
    var reads =
        Set.of("list", "detail", "expectations", "chequeIds", "cheque", "chequeEvents", "refunds");
    var reversals = Set.of("reverse", "cancelRefund");
    for (var method : CollectionController.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) continue;
      assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
      assertThat(method.getAnnotation(PreAuthorize.class).value())
          .isEqualTo(
              "@auth.can(authentication, 'finance', '"
                  + (reads.contains(method.getName())
                      ? "read"
                      : reversals.contains(method.getName()) ? "reverse" : "write")
                  + "')");
    }
  }
}
