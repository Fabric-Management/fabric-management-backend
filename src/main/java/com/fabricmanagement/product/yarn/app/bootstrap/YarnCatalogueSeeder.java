package com.fabricmanagement.product.yarn.app.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.facade.PropertyRegistryFacade;
import com.fabricmanagement.product.yarn.domain.reference.YarnEndUse;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.infra.repository.YarnEndUseRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnSpinningSystemRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnTestMethodRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class YarnCatalogueSeeder {

  public static final String TWIST_PROPERTY_KEY = "YARN_TWIST_TPM";

  private static final List<SpinningSystemSeed> SPINNING_SYSTEMS =
      List.of(
          new SpinningSystemSeed(
              "RING",
              "Ring",
              "Conventional ring-spinning system.",
              1,
              SpinningTechnologyFamily.RING),
          new SpinningSystemSeed(
              "ROTOR", "Rotor", "Rotor spinning system.", 2, SpinningTechnologyFamily.ROTOR),
          new SpinningSystemSeed(
              "OPEN_END",
              "Open End",
              "Tenant-facing open-end label mapped to the rotor family.",
              3,
              SpinningTechnologyFamily.ROTOR),
          new SpinningSystemSeed(
              "AIR_JET",
              "Air Jet",
              "Air-jet spinning system.",
              4,
              SpinningTechnologyFamily.AIR_JET),
          new SpinningSystemSeed(
              "FRICTION",
              "Friction",
              "Friction spinning system.",
              5,
              SpinningTechnologyFamily.FRICTION),
          new SpinningSystemSeed(
              "COMPACT",
              "Compact",
              "Compact ring-spinning label mapped to the ring family.",
              6,
              SpinningTechnologyFamily.RING));

  private static final List<EndUseSeed> END_USES =
      List.of(
          new EndUseSeed("SEWING", "Sewing Yarn", "Yarn used for sewing operations", 1),
          new EndUseSeed("KNITTING", "Knitting Yarn", "Yarn used for knitting fabric", 2),
          new EndUseSeed("WEAVING", "Weaving Yarn", "Yarn used for weaving fabric on loom", 3),
          new EndUseSeed("EMBROIDERY", "Embroidery Yarn", "Decorative embroidery yarn", 4));

  private static final List<TestMethodSeed> TEST_METHODS =
      List.of(
          new TestMethodSeed(
              "ISO_2061",
              "ISO 2061",
              "Direct counting method for determining yarn twist.",
              1,
              "ISO 2061",
              null,
              TWIST_PROPERTY_KEY),
          new TestMethodSeed(
              "ISO_17202",
              "ISO 17202",
              "Untwist/retwist method for determining twist in single spun yarns.",
              2,
              "ISO 17202",
              null,
              TWIST_PROPERTY_KEY),
          new TestMethodSeed(
              "SUPPLIER_DECLARED",
              "Supplier Declared",
              "Method declared by the supplier without a restricted registry property.",
              3,
              null,
              null,
              null));

  private final YarnSpinningSystemRepository spinningSystemRepository;
  private final YarnEndUseRepository endUseRepository;
  private final YarnTestMethodRepository testMethodRepository;
  private final PropertyRegistryFacade propertyRegistryFacade;
  private final TransactionTemplate transactionTemplate;

  public static List<SpinningSystemSeed> spinningSystems() {
    return SPINNING_SYSTEMS;
  }

  public static List<EndUseSeed> endUses() {
    return END_USES;
  }

  public static List<TestMethodSeed> testMethods() {
    return TEST_METHODS;
  }

  public int seed(UUID tenantId) {
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            transactionTemplate.execute(
                status ->
                    seedSpinningSystems(tenantId)
                        + seedEndUses(tenantId)
                        + seedTestMethods(tenantId)));
  }

  private int seedSpinningSystems(UUID tenantId) {
    Map<String, YarnSpinningSystem> existing =
        spinningSystemRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(YarnSpinningSystem::getCode, Function.identity()));
    List<YarnSpinningSystem> missing =
        SPINNING_SYSTEMS.stream()
            .filter(
                seed ->
                    isMissingOrLogCollision(
                        "spinning system",
                        tenantId,
                        seed.code(),
                        existing,
                        YarnSpinningSystem::isSystemDefined))
            .map(
                seed ->
                    YarnSpinningSystem.defineSystem(
                        tenantId,
                        seed.code(),
                        seed.name(),
                        seed.description(),
                        seed.displayOrder(),
                        seed.technologyFamily()))
            .toList();
    spinningSystemRepository.saveAll(missing);
    return missing.size();
  }

  private int seedEndUses(UUID tenantId) {
    Map<String, YarnEndUse> existing =
        endUseRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(YarnEndUse::getCode, Function.identity()));
    List<YarnEndUse> missing =
        END_USES.stream()
            .filter(
                seed ->
                    isMissingOrLogCollision(
                        "end-use", tenantId, seed.code(), existing, YarnEndUse::isSystemDefined))
            .map(
                seed ->
                    YarnEndUse.defineSystem(
                        tenantId,
                        seed.code(),
                        seed.name(),
                        seed.description(),
                        seed.displayOrder()))
            .toList();
    endUseRepository.saveAll(missing);
    return missing.size();
  }

  private int seedTestMethods(UUID tenantId) {
    TEST_METHODS.stream()
        .map(TestMethodSeed::applicablePropertyKey)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .forEach(propertyKey -> propertyRegistryFacade.resolve(tenantId, propertyKey));

    Map<String, YarnTestMethod> existing =
        testMethodRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(YarnTestMethod::getCode, Function.identity()));
    List<YarnTestMethod> missing =
        TEST_METHODS.stream()
            .filter(
                seed ->
                    isMissingOrLogCollision(
                        "test method",
                        tenantId,
                        seed.code(),
                        existing,
                        YarnTestMethod::isSystemDefined))
            .map(
                seed ->
                    YarnTestMethod.defineSystem(
                        tenantId,
                        seed.code(),
                        seed.name(),
                        seed.description(),
                        seed.displayOrder(),
                        seed.standardRef(),
                        seed.instrument(),
                        seed.applicablePropertyKey()))
            .toList();
    testMethodRepository.saveAll(missing);
    return missing.size();
  }

  private <T> boolean isMissingOrLogCollision(
      String catalogue,
      UUID tenantId,
      String code,
      Map<String, T> existing,
      Predicate<T> isSystemDefined) {
    T row = existing.get(code);
    if (row == null) {
      return true;
    }
    if (!isSystemDefined.test(row)) {
      log.error(
          "Yarn system catalogue collision retained for explicit repair: "
              + "catalogue={}, tenant={}, code={}",
          catalogue,
          tenantId,
          code);
    }
    return false;
  }

  public record SpinningSystemSeed(
      String code,
      String name,
      String description,
      Integer displayOrder,
      SpinningTechnologyFamily technologyFamily) {}

  public record EndUseSeed(String code, String name, String description, Integer displayOrder) {}

  public record TestMethodSeed(
      String code,
      String name,
      String description,
      Integer displayOrder,
      String standardRef,
      String instrument,
      String applicablePropertyKey) {}
}
