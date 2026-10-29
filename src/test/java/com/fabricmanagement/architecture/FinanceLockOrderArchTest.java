package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.fabricmanagement.finance.collection.app.CollectionCommandStore;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Locks are always taken in one order: the finance tenant lock first, a sales order row second.
 * Finance takes the order's share lock while it holds the tenant-wide finance lock (see {@code
 * CollectionSourcePort}); a sales transaction that held an order lock and then asked for the
 * finance lock would wait for finance while finance waits for it, and neither would ever finish.
 * Sales can only do that if it can reach code that takes the finance lock, so these rules close the
 * ways to.
 */
class FinanceLockOrderArchTest {
  private static final String FINANCE = "com.fabricmanagement.finance";

  private static com.tngtech.archunit.core.domain.JavaClasses production(String... packages) {
    return new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages(packages);
  }

  /** Finance code sales may know: the two collection ports and the vocabulary they speak. */
  private static DescribedPredicate<JavaClass> financeBeyondTheCollectionPorts() {
    return DescribedPredicate.describe(
        "finance types other than the collection ports and their vocabulary",
        type ->
            type.getPackageName().startsWith(FINANCE + ".")
                && !type.getPackageName().startsWith(FINANCE + ".collection.app.port")
                && !type.getName().startsWith(FINANCE + ".collection.domain.CollectionTypes"));
  }

  @Test
  void nothingOutsideFinanceTakesTheFinanceTenantLock() {
    var production = production("com.fabricmanagement");
    noClasses()
        .that()
        .resideOutsideOfPackage("..finance..")
        .should()
        .callMethod(PaymentRepository.class, "lockFinanceTenant", UUID.class)
        .orShould()
        .callMethod(PaymentRepository.class, "tryLockFinanceTenant", UUID.class)
        .check(production);
    noClasses()
        .that()
        .resideOutsideOfPackage("..finance..")
        .should()
        .callMethod(CollectionCommandStore.class, "lock")
        .orShould()
        .callMethod(CollectionCommandStore.class, "tryLock")
        .check(production);
  }

  @Test
  void salesReachesFinanceOnlyThroughTheCollectionPorts() {
    noClasses()
        .that()
        .resideInAPackage("..sales..")
        .should()
        .dependOnClassesThat(financeBeyondTheCollectionPorts())
        .check(production("com.fabricmanagement.sales"));
  }

  @Test
  void theCollectionPortsExposeReadsOnlyAndTakeNoFinanceLock() {
    // The ports sales calls are plain reads: they never take the finance lock, so a sales
    // transaction holding an order lock cannot end up waiting for finance through them.
    noClasses()
        .that()
        .resideInAPackage("..finance.collection.app.adapter..")
        .and()
        .haveSimpleName("CollectionSettlementAdapter")
        .should()
        .callMethod(CollectionCommandStore.class, "lock")
        .orShould()
        .callMethod(CollectionCommandStore.class, "tryLock")
        .orShould()
        .callMethod(PaymentRepository.class, "lockFinanceTenant", UUID.class)
        .orShould()
        .callMethod(PaymentRepository.class, "tryLockFinanceTenant", UUID.class)
        .check(production("com.fabricmanagement.finance.collection"));
  }
}
