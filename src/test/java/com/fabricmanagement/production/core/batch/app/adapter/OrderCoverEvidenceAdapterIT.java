package com.fabricmanagement.production.core.batch.app.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.product.core.app.ProductEvidenceQueryService;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.product.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.product.qualitygrade.infra.repository.QualityGradeRepository;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.app.StockAvailabilityQueryService;
import com.fabricmanagement.production.core.batch.domain.*;
import com.fabricmanagement.production.core.batch.infra.repository.*;
import com.fabricmanagement.production.core.stockunit.domain.*;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidenceEvaluator;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverFingerprint;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.*;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.Suitability;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
class OrderCoverEvidenceAdapterIT extends AbstractIntegrationTest {
  @Autowired private OrderCoverEvidenceAdapter adapter;
  @Autowired private StockAvailabilityQueryService availability;
  @Autowired private BatchPrimaryMeasureService measures;
  @Autowired private ProductEvidenceQueryService productReferences;
  @Autowired private BatchLotQuantityIntentRepository intents;
  @Autowired private BatchRepository batches;
  @Autowired private StockUnitRepository units;
  @Autowired private BatchReservationRepository reservations;
  @Autowired private ProductRepository products;
  @Autowired private QualityGradeRepository grades;
  @Autowired private TenantRepository tenants;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;
  private UUID tenantId;

  @BeforeEach
  void tenantContext() {
    tenantId = tenant();
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(UUID.randomUUID());
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void batchesBeyondPresentationPageAreAllInspected() {
    Product product = product();
    for (int i = 0; i < 26; i++) batch(product, BatchStatus.AVAILABLE);
    assertThat(
            availability.lots(null, null, product.getId(), null, null, null, PageRequest.of(0, 20)))
        .hasSize(20);
    assertThat(adapter.inspect(requirements(product, "100", "M")).lots()).hasSize(26);
  }

  @Test
  void heldBatchStaysReferencedButCannotContributeSuitableStock() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.ON_HOLD);
    var inputs = adapter.inspect(requirements(product, "10", "M"));
    assertThat(inputs.lots()).hasSize(1);
    assertThat(inputs.lots().getFirst().eligibility()).isEqualTo(Eligibility.EXCLUDED);
    assertThat(inputs.lots().getFirst().reasons()).contains("BATCH_ON_HOLD");
    assertThat(inputs.lots().getFirst().sources())
        .anySatisfy(source -> assertThat(source.id()).isEqualTo(batch.getId()));
  }

  @Test
  void convertsSameDimensionAndNeverGuessesWeightToLength() {
    Product product = product();
    batch(product, BatchStatus.AVAILABLE);
    var converted = adapter.inspect(requirements(product, "2500", "CM"));
    assertThat(converted.demands().getFirst().quantity()).isEqualByComparingTo("25");
    assertThat(converted.demands().getFirst().unit()).isEqualTo("M");
    assertThat(converted.lots().getFirst().sources())
        .anySatisfy(source -> assertThat(source.type()).isEqualTo("PRIMARY_MEASURE_RULE"));
    var unknown = adapter.inspect(requirements(product, "25", "KG"));
    assertThat(unknown.demands().getFirst().quantity()).isNull();
    assertThat(unknown.demands().getFirst().reason()).isEqualTo("UNSUPPORTED_UNIT_CONVERSION");
  }

  @Test
  void mixedGradeCommitmentsStayUnknownAndSourceChangesInvalidateFingerprint() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    var good = grade(true, 1);
    var bad = grade(false, 2);
    piece(batch, good, "100");
    piece(batch, bad, "100");
    var reservation =
        reservations.saveAndFlush(
            BatchReservation.create(
                tenantId,
                batch.getId(),
                UUID.randomUUID(),
                "WORK_ORDER",
                new BigDecimal("50"),
                "M",
                null));
    var before = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(before.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    assertThat(before.reasons()).contains("COMMITMENT_GRADE_SCOPE_UNKNOWN", "NON_SALEABLE_GRADE");
    assertThat(before.suitableFree()).isNull();
    assertThat(before.sources())
        .anySatisfy(
            source -> {
              assertThat(source.type()).isEqualTo("BATCH_RESERVATION");
              assertThat(source.id()).isEqualTo(reservation.getId());
            });
    reservation.cancel();
    reservations.saveAndFlush(reservation);
    var after = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(after.suitableFree()).isEqualByComparingTo("100");
    assertThat(after.sourceFingerprint()).isNotEqualTo(before.sourceFingerprint());
  }

  @Test
  void homogeneousSaleableStockUsesExistingCanonicalFreeArithmetic() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    reservations.saveAndFlush(
        BatchReservation.create(
            tenantId,
            batch.getId(),
            UUID.randomUUID(),
            "WORK_ORDER",
            new BigDecimal("2000"),
            "CM",
            null));
    var row = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
    assertThat(row.sources())
        .anySatisfy(
            source -> {
              assertThat(source.type()).isEqualTo("QUALITY_GRADE");
              assertThat(source.revision()).isNotNull();
            });
    assertThat(row.suitableFree()).isEqualByComparingTo("80");
    assertThat(row.suitableFree())
        .isEqualByComparingTo(
            availability.lotsForProducts(Set.of(product.getId())).getFirst().free());
  }

  @Test
  void unsupportedCommitmentUnitCannotBeHiddenByCanonicalZero() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    reservations.saveAndFlush(
        BatchReservation.create(
            tenantId,
            batch.getId(),
            UUID.randomUUID(),
            "WORK_ORDER",
            new BigDecimal("20"),
            "KG",
            null));
    var row = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    assertThat(row.reasons()).contains("UNIT_MISMATCH");
    assertThat(row.suitableFree()).isNull();
  }

  @Test
  void missingLengthInOneReleasedPieceDoesNotBecomeKnownPartialTotal() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    var grade = grade(true, 1);
    piece(batch, grade, "100");
    piece(batch, grade, null);
    var row = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    assertThat(row.reasons()).contains("PRIMARY_MEASUREMENT_UNKNOWN");
  }

  @Test
  void unassignedGradeAndFallbackAreUnknown() {
    Product product = product();
    Batch fallback = batch(product, BatchStatus.AVAILABLE);
    assertThat(adapter.inspect(requirements(product, "80", "M")).lots().getFirst().reasons())
        .contains("BATCH_FALLBACK_QUALITY_UNKNOWN");
    piece(fallback, null, "100");
    var row = adapter.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.reasons()).contains("QUALITY_GRADE_UNKNOWN");
    assertThat(row.suitableFree()).isNull();
  }

  @Test
  void tenantMismatchIsRejectedAndOtherTenantsPopulationIsInvisible() {
    Product product = product();
    batch(product, BatchStatus.AVAILABLE);
    Requirements original = requirements(product, "100", "M");
    UUID otherTenant = tenant();
    TenantContext.setCurrentTenantId(otherTenant);
    assertThatThrownBy(() -> adapter.inspect(original))
        .isInstanceOf(
            com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException.class);
    var other =
        new Requirements(
            otherTenant,
            original.orderId(),
            original.caseId(),
            original.orderVersion(),
            original.lines());
    assertThat(adapter.inspect(other).lots()).isEmpty();
  }

  @Test
  void lockingSeamReadsSameInputsInsideCallerTransaction() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    Requirements requirements = requirements(product, "80", "M");
    var before = adapter.inspect(requirements);
    var locked = adapter.lockAndInspect(requirements);
    assertThat(OrderCoverFingerprint.of(locked)).isEqualTo(OrderCoverFingerprint.of(before));
    assertThat(entityManager.createNativeQuery("show lock_timeout").getSingleResult())
        .isEqualTo("5s");
  }

  @Test
  void noBatchesStillUsesCatalogMeasureAndProvesKnownShortfallForCompleteDemand() {
    Product product = product();
    Requirements original = requirements(product, "8000", "CM");
    Requirement line = original.lines().getFirst();
    Requirements complete =
        new Requirements(
            tenantId,
            original.orderId(),
            original.caseId(),
            0,
            List.of(
                new Requirement(
                    line.lineId(),
                    0,
                    product.getId(),
                    line.createdAt(),
                    line.requested(),
                    line.unit(),
                    true,
                    null,
                    "typed-test",
                    null)));
    var inputs = adapter.inspect(complete);
    assertThat(inputs.lots()).isEmpty();
    assertThat(inputs.demands().getFirst().quantity()).isEqualByComparingTo("80");
    assertThat(inputs.demands().getFirst().unit()).isEqualTo("M");
    assertThat(inputs.demands().getFirst().sources())
        .anySatisfy(
            source -> {
              assertThat(source.type()).isEqualTo("PRODUCT");
              assertThat(source.id()).isEqualTo(product.getId());
              assertThat(source.revision()).isEqualTo(product.getVersion());
            });
    var evidence = OrderCoverEvidenceEvaluator.evaluate(complete, inputs).getFirst();
    assertThat(evidence.suitability()).isEqualTo(Suitability.NO_MATCH);
    assertThat(evidence.shortfall().value()).isEqualTo("80");
  }

  @Test
  void differingGradeReferencePopulationReturnsUnknownInsteadOfNullPointerException() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    var missingReferences = mock(QualityGradeQueryService.class);
    var subject =
        new OrderCoverEvidenceAdapter(
            availability,
            measures,
            batches,
            units,
            intents,
            reservations,
            missingReferences,
            productReferences,
            entityManager);
    var row = subject.inspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(row.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    assertThat(row.suitableFree()).isNull();
    assertThat(row.reasons()).contains("QUALITY_GRADE_UNKNOWN");
    assertThat(row.comparisons())
        .anySatisfy(
            comparison -> {
              assertThat(comparison.dimension()).isEqualTo("QUALITY_SALEABILITY");
              assertThat(comparison.result()).isEqualTo(ComparisonResult.UNKNOWN);
            });
  }

  @Test
  void lockingSeamPreservesPendingCallerEdits() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    StockUnit piece = piece(batch, grade(true, 1), "100");
    long version = piece.getVersion();
    piece.recordLength(new BigDecimal("75"), "M");
    var row = adapter.lockAndInspect(requirements(product, "80", "M")).lots().getFirst();
    assertThat(piece.getLength()).isEqualByComparingTo("75");
    assertThat(piece.getVersion()).isGreaterThan(version);
    assertThat(row.suitableFree()).isEqualByComparingTo("75");
    assertThat(entityManager.contains(piece)).isTrue();
  }

  @Test
  void staleManagedRowsAreRejectedRatherThanSilentlyRefreshedOrUsed() {
    Product product = product();
    Batch batch = batch(product, BatchStatus.AVAILABLE);
    piece(batch, grade(true, 1), "100");
    long version = batch.getVersion();
    entityManager
        .createNativeQuery(
            "update production.production_execution_batch set version = version + 1"
                + " where tenant_id = :tenant and id = :id")
        .setParameter("tenant", tenantId)
        .setParameter("id", batch.getId())
        .executeUpdate();
    assertThatThrownBy(() -> adapter.lockAndInspect(requirements(product, "80", "M")))
        .isInstanceOf(OptimisticLockException.class);
    assertThat(batch.getVersion()).isEqualTo(version);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void measuresDistinctConcurrentOrdersContendingForOnePopularProduct() throws Exception {
    var transaction = new TransactionTemplate(transactionManager);
    Product product =
        transaction.execute(
            status -> {
              Product created = product();
              piece(batch(created, BatchStatus.AVAILABLE), grade(true, 1), "100");
              return created;
            });
    int count = 8;
    var ready = new CountDownLatch(count);
    var start = new CountDownLatch(1);
    List<Future<Long>> futures = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(count)) {
      for (int i = 0; i < count; i++) {
        // Independent order and line identities; only their product is shared.
        Requirements order = requirements(product, "80", "M");
        futures.add(
            executor.submit(
                () -> {
                  TenantContext.setCurrentTenantId(tenantId);
                  TenantContext.setCurrentUserId(UUID.randomUUID());
                  ready.countDown();
                  try {
                    if (!start.await(10, TimeUnit.SECONDS))
                      throw new AssertionError("Start barrier timed out");
                    long begin = System.nanoTime();
                    transaction.executeWithoutResult(
                        status -> {
                          var inputs = adapter.lockAndInspect(order);
                          assertThat(inputs.lots().getFirst().suitableFree())
                              .isEqualByComparingTo("100");
                          // Controlled representative downstream work; these locks remain held
                          // until commit.
                          try {
                            Thread.sleep(100);
                          } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(ex);
                          }
                        });
                    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
                  } finally {
                    TenantContext.clear();
                  }
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      long begin = System.nanoTime();
      start.countDown();
      List<Long> durations = new ArrayList<>();
      for (var future : futures) durations.add(future.get(30, TimeUnit.SECONDS));
      long wall = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
      durations.sort(Long::compareTo);
      System.out.printf(
          Locale.ROOT,
          "ORDER_COVER_CONTENTION orders=%d products=1 hold_ms=100 success=%d timeout=0 p50_ms=%d p95_ms=%d max_ms=%d wall_ms=%d%n",
          count,
          durations.size(),
          durations.get(3),
          durations.get(7),
          durations.getLast(),
          wall);
      assertThat(durations).hasSize(count);
    } finally {
      start.countDown();
    }
  }

  private UUID tenant() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Tenant tenant = Tenant.create("Evidence " + suffix, "EVID-" + suffix);
    tenant.activate("test");
    return tenants.saveAndFlush(tenant).getId();
  }

  private Product product() {
    Product product = Product.create(ProductType.FABRIC, "M");
    product.setTenantId(tenantId);
    return products.saveAndFlush(product);
  }

  private Batch batch(Product product, BatchStatus status) {
    var batch =
        Batch.builder()
            .productId(product.getId())
            .productType(ProductType.FABRIC)
            .batchCode("EVID-" + UUID.randomUUID().toString().substring(0, 8))
            .quantity(new BigDecimal("200"))
            .unit("M")
            .reservedQuantity(BigDecimal.ZERO)
            .consumedQuantity(BigDecimal.ZERO)
            .wasteQuantity(BigDecimal.ZERO)
            .status(status)
            .sourceType(BatchSourceType.INITIAL_STOCK)
            .build();
    batch.setTenantId(tenantId);
    return batches.saveAndFlush(batch);
  }

  private QualityGrade grade(boolean saleable, int rank) {
    return grades.saveAndFlush(
        QualityGrade.create(
            tenantId,
            ProductType.FABRIC,
            "E" + UUID.randomUUID().toString().substring(0, 6),
            "Evidence grade",
            rank,
            BigDecimal.ONE,
            saleable,
            false,
            null,
            false));
  }

  private StockUnit piece(Batch batch, QualityGrade grade, String metres) {
    var piece =
        StockUnit.create(
            tenantId,
            batch.getId(),
            ProductType.FABRIC,
            "EVID-" + UUID.randomUUID(),
            null,
            PackageType.ROLL,
            new BigDecimal("10"),
            null,
            "KG",
            null,
            StockUnitSourceType.PRODUCTION,
            UUID.randomUUID(),
            QualityDisposition.RELEASED);
    if (metres != null) piece.recordLength(new BigDecimal(metres), "M");
    if (grade != null) piece.changeGrade(grade.getId());
    return units.saveAndFlush(piece);
  }

  private Requirements requirements(Product product, String quantity, String unit) {
    var line =
        new Requirement(
            UUID.randomUUID(),
            0,
            product.getId(),
            Instant.EPOCH,
            new BigDecimal(quantity),
            unit,
            false,
            "REQUIREMENT_COMPLETENESS_UNKNOWN",
            "test",
            null);
    return new Requirements(tenantId, UUID.randomUUID(), UUID.randomUUID(), 0, List.of(line));
  }
}
