package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.common.exception.ForbiddenOperationException;
import com.fabricmanagement.product.core.app.ProductService;
import com.fabricmanagement.product.fiber.domain.FiberCatalogScope;
import com.fabricmanagement.product.fiber.domain.FiberKind;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.product.fiber.dto.FiberActionBlockedReason;
import com.fabricmanagement.product.fiber.dto.FiberCompositionComponentDto;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FIBER-CATALOG-1 §4/§7 on a real database: shared pure fibres are read-only for tenants and are
 * accepted as blend components; blends carry no ISO and present complete, exact components.
 * Scenarios A04 (service level), A05, A06, A07, A08.
 */
class FiberSharedBlendIT extends FiberSourceIntegrationSupport {

  @Autowired private ProductService productService;

  @Test
  void a05_blendOfTwoSharedPureFibresIsTenantOwnedWithNullIsoAndCompleteComponents() {
    UUID tenantA = insertTenant("blend-a");
    UUID tenantB = insertTenant("blend-b");
    useTenant(tenantA, UUID.randomUUID());

    FiberDto blend = fiberService.createFiber(blendRequest(null, "60", "40"));

    assertThat(blend.getKind()).isEqualTo(FiberKind.BLEND);
    assertThat(blend.getCatalogScope()).isEqualTo(FiberCatalogScope.TENANT);
    assertThat(blend.getTenantId()).isEqualTo(tenantA);
    assertThat(blend.getFiberIsoCodeId()).isNull();
    assertThat(blend.getIsoCode()).isNull();
    assertThat(blend.getFiberCategoryId()).isEqualTo(MIXED_BLEND_CATEGORY_ID);
    assertThat(blend.getFiberName()).isEqualTo("60% CO / 40% PES");
    assertThat(blend.getCompositionLabel()).isEqualTo("60% CO / 40% PES");
    assertThat(blend.getComponents())
        .extracting(FiberCompositionComponentDto::fiberId, FiberCompositionComponentDto::isoCode)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(canonicalFiberId("CO"), "CO"),
            org.assertj.core.groups.Tuple.tuple(canonicalFiberId("PES"), "PES"));
    assertThat(
            queryOne(
                "SELECT tenant_id FROM production.prod_product WHERE id = ?",
                UUID.class,
                blend.getProductId()))
        .isEqualTo(tenantA);

    useTenant(tenantB, UUID.randomUUID());
    assertThat(fiberService.getById(blend.getId())).isEmpty();
    assertThatThrownBy(
            () ->
                fiberService.updateFiber(
                    blend.getId(),
                    UpdateFiberRequest.builder().version(0L).fiberName("Stolen").build()))
        .isInstanceOf(FiberDomainException.class)
        .satisfies(
            failure -> assertThat(((FiberDomainException) failure).getHttpStatus()).isEqualTo(404));
  }

  @Test
  void a04_sharedFibreAndItsProductAreReadOnlyForTenantsButReadable() {
    UUID tenantId = insertTenant("shared-read-only");
    useTenant(tenantId, UUID.randomUUID());
    UUID cotton = canonicalFiberId("CO");

    FiberDto shared = fiberService.getById(cotton).orElseThrow();
    assertThat(shared.getCatalogScope()).isEqualTo(FiberCatalogScope.SHARED);
    assertThat(shared.getComponents())
        .singleElement()
        .satisfies(component -> assertThat(component.percentage()).isEqualByComparingTo("100"));
    assertThat(shared.getCapabilities())
        .allSatisfy(
            capability -> {
              assertThat(capability.allowed()).isFalse();
              assertThat(capability.reason())
                  .isEqualTo(FiberActionBlockedReason.FIBER_SHARED_READ_ONLY);
            });

    assertSharedReadOnly(
        () ->
            fiberService.updateFiber(
                cotton, UpdateFiberRequest.builder().version(0L).fiberName("Mine").build()));
    assertSharedReadOnly(() -> fiberService.deactivateFiber(cotton));
    assertSharedReadOnly(
        () ->
            fiberService.updateFiber(
                cotton,
                UpdateFiberRequest.builder()
                    .version(0L)
                    .fiberName("Cotton (100%)")
                    .materialSource(MaterialSource.RECYCLED)
                    .build()));
    assertThatThrownBy(() -> productService.deactivateProduct(canonicalProductId("CO")))
        .isInstanceOf(ForbiddenOperationException.class)
        .extracting("errorCode")
        .isEqualTo("PRODUCT_SHARED_READ_ONLY");
    assertThat(productService.exists(tenantId, canonicalProductId("CO"))).isTrue();
    assertThat(
            queryOne(
                "SELECT fiber_name FROM production.prod_fiber WHERE id = ?", String.class, cotton))
        .isEqualTo("Cotton (100%)");
  }

  @Test
  void a06_invalidCompositionsAreRejectedWithNamedCodes() {
    UUID tenantA = insertTenant("invalid-a");
    UUID tenantB = insertTenant("invalid-b");
    UUID foreignVariant =
        insertPrivateVariant(tenantB, "PES", CATEGORY, "B recycled PES", MaterialSource.RECYCLED);
    UUID ownBlend =
        insertBlend(
            tenantA,
            "Own blend",
            "{\"" + canonicalFiberId("CO") + "\":50,\"" + canonicalFiberId("LI") + "\":50}");
    UUID inactive =
        insertPrivateVariant(
            tenantA, "CV", "REGENERATED_CELLULOSIC", "Old CV", MaterialSource.VIRGIN);
    update("UPDATE production.prod_fiber SET is_active = FALSE WHERE id = ?", inactive);
    UUID obsolete =
        insertPrivateVariant(tenantA, "WO", "NATURAL_ANIMAL", "Obsolete WO", MaterialSource.VIRGIN);
    update("UPDATE production.prod_fiber SET status = 'OBSOLETE' WHERE id = ?", obsolete);
    useTenant(tenantA, UUID.randomUUID());
    UUID co = canonicalFiberId("CO");
    UUID pes = canonicalFiberId("PES");

    assertRejected(
        Map.of(co, pct("60"), UUID.randomUUID(), pct("40")), "FIBER_COMPONENT_NOT_FOUND");
    assertRejected(Map.of(co, pct("60"), foreignVariant, pct("40")), "FIBER_COMPONENT_NOT_FOUND");
    assertRejected(
        Map.of(co, pct("60"), canonicalProductId("PES"), pct("40")), "FIBER_COMPONENT_NOT_FOUND");
    assertRejected(Map.of(co, pct("60"), inactive, pct("40")), "FIBER_COMPONENT_INACTIVE");
    assertRejected(Map.of(co, pct("60"), obsolete, pct("40")), "FIBER_COMPONENT_INACTIVE");
    assertRejected(Map.of(pes, pct("60"), ownBlend, pct("40")), "FIBER_COMPONENT_NOT_PURE");
    Map<UUID, BigDecimal> withNull = new LinkedHashMap<>();
    withNull.put(co, null);
    withNull.put(pes, pct("100"));
    assertRejected(withNull, "FIBER_COMPOSITION_PERCENTAGE_REQUIRED");
    assertRejected(ordered(co, pct("0"), pes, pct("100")), "FIBER_COMPOSITION_ZERO_PERCENTAGE");
    assertRejected(
        ordered(co, pct("-10"), pes, pct("110")), "FIBER_COMPOSITION_NEGATIVE_PERCENTAGE");
    assertRejected(Map.of(co, pct("60"), pes, pct("39.99")), "FIBER_COMPOSITION_TOTAL_NOT_100");
    assertRejected(Map.of(co, pct("96"), pes, pct("4")), "FIBER_COMPOSITION_MIN_RATIO_NOT_MET");
    assertRejected(Map.of(co, pct("100")), "FIBER_BLEND_MIN_COMPONENTS");
    Map<UUID, BigDecimal> six = new LinkedHashMap<>();
    for (String code : List.of("CO", "PES", "LI", "PA", "CV", "WO")) {
      six.put(canonicalFiberId(code), code.equals("CO") ? pct("50") : pct("10"));
    }
    assertRejected(six, "FIBER_COMPOSITION_MAX_COMPONENTS_EXCEEDED");
    assertThat(count("SELECT count(*) FROM production.prod_product WHERE tenant_id = ?", tenantA))
        .as("no rejected blend left a product behind")
        .isEqualTo(3L);
  }

  @Test
  void a07_decimalsRoundTripAndScaleOrMapOrderDoNotChangeDuplicateIdentity() throws Exception {
    UUID tenantId = insertTenant("decimal");
    useTenant(tenantId, UUID.randomUUID());

    FiberDto blend = fiberService.createFiber(blendRequest("Precise blend", "62.5", "37.5"));

    assertThat(blend.getCompositionLabel()).isEqualTo("62.5% CO / 37.5% PES");
    assertThat(blend.getComponents())
        .extracting(FiberCompositionComponentDto::percentage)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("62.5"), new BigDecimal("37.5"));
    assertThat(fiberService.getById(blend.getId()).orElseThrow().getCompositionLabel())
        .isEqualTo("62.5% CO / 37.5% PES");

    Map<UUID, BigDecimal> reordered = new LinkedHashMap<>();
    reordered.put(canonicalFiberId("PES"), new BigDecimal("37.50"));
    reordered.put(canonicalFiberId("CO"), new BigDecimal("62.500"));
    assertThatThrownBy(
            () ->
                fiberService.createFiber(
                    CreateFiberRequest.builder().unit("KG").composition(reordered).build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_DUPLICATE_COMPOSITION");

    List<Throwable> outcomes =
        runTogether(
            () -> createAs(tenantId, "70", "30"), () -> createAs(tenantId, "70.00", "30.0"));
    assertThat(outcomes.stream().filter(Objects::isNull)).hasSize(1);
    assertThat(outcomes.stream().filter(Objects::nonNull))
        .singleElement()
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_DUPLICATE_COMPOSITION");
  }

  @Test
  void a08_virginAndRecycledPesStayDistinctComponentsOnTheSameSharedIso() {
    UUID tenantId = insertTenant("source-blend");
    UUID virgin =
        insertPrivateVariant(tenantId, "PES", CATEGORY, "PES virgin", MaterialSource.VIRGIN);
    UUID recycled =
        insertPrivateVariant(tenantId, "PES", CATEGORY, "PES recycled", MaterialSource.RECYCLED);
    useTenant(tenantId, UUID.randomUUID());

    FiberDto blend =
        fiberService.createFiber(
            CreateFiberRequest.builder()
                .unit("KG")
                .composition(Map.of(virgin, pct("50"), recycled, pct("50")))
                .build());

    assertThat(blend.getMaterialSource()).isNull();
    assertThat(blend.getComponents()).hasSize(2);
    assertThat(blend.getComponents())
        .extracting(FiberCompositionComponentDto::isoCodeId)
        .containsOnly(sharedIsoId("PES"));
    assertThat(blend.getComponents())
        .extracting(FiberCompositionComponentDto::materialSource)
        .containsExactly(MaterialSource.RECYCLED, MaterialSource.VIRGIN);
    assertThat(blend.getCompositionLabel()).isEqualTo("50% PES (recycled) / 50% PES (virgin)");
  }

  private Throwable createAs(UUID tenantId, String co, String pes) {
    try {
      useTenant(tenantId, UUID.randomUUID());
      fiberService.createFiber(blendRequest(null, co, pes));
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      TenantContext.clear();
    }
  }

  private CreateFiberRequest blendRequest(String name, String co, String pes) {
    return CreateFiberRequest.builder()
        .unit("KG")
        .fiberName(name)
        .composition(Map.of(canonicalFiberId("CO"), pct(co), canonicalFiberId("PES"), pct(pes)))
        .build();
  }

  private void assertRejected(Map<UUID, BigDecimal> composition, String code) {
    assertThatThrownBy(
            () ->
                fiberService.createFiber(
                    CreateFiberRequest.builder().unit("KG").composition(composition).build()))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  private static void assertSharedReadOnly(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(FiberDomainException.class)
        .satisfies(
            failure -> {
              FiberDomainException exception = (FiberDomainException) failure;
              assertThat(exception.getErrorCode()).isEqualTo("FIBER_SHARED_READ_ONLY");
              assertThat(exception.getHttpStatus()).isEqualTo(403);
            });
  }

  private static Map<UUID, BigDecimal> ordered(
      UUID first, BigDecimal firstShare, UUID second, BigDecimal secondShare) {
    Map<UUID, BigDecimal> composition = new LinkedHashMap<>();
    composition.put(first, firstShare);
    composition.put(second, secondShare);
    return composition;
  }

  private static BigDecimal pct(String value) {
    return new BigDecimal(value);
  }

  private List<Throwable> runTogether(Callable<Throwable> first, Callable<Throwable> second)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Throwable>> futures = new ArrayList<>();
      for (Callable<Throwable> task : List.of(first, second)) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await(10, TimeUnit.SECONDS);
                  return task.call();
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<Throwable> outcomes = new ArrayList<>();
      for (Future<Throwable> future : futures) {
        outcomes.add(future.get(20, TimeUnit.SECONDS));
      }
      return outcomes;
    } finally {
      executor.shutdownNow();
    }
  }
}
