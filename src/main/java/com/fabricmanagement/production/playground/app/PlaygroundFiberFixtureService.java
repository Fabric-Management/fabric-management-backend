package com.fabricmanagement.production.playground.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberCertificationQueryService;
import com.fabricmanagement.product.fiber.app.FiberPlaygroundFixtureService;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.product.fiber.dto.FiberCatalogReferenceDto;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.production.core.batch.app.BatchCertificationService;
import com.fabricmanagement.production.core.batch.app.BatchService;
import com.fabricmanagement.production.core.batch.domain.BatchCertificateKind;
import com.fabricmanagement.production.core.batch.domain.BatchCertificationScope;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.dto.AddBatchCertificationRequest;
import com.fabricmanagement.production.core.batch.dto.BatchCertificationDto;
import com.fabricmanagement.production.core.batch.dto.BatchDto;
import com.fabricmanagement.production.core.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.core.stockunit.app.StockUnitService;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.playground.domain.PlaygroundFixtureItem;
import com.fabricmanagement.production.playground.domain.PlaygroundFixtureOrigin;
import com.fabricmanagement.production.playground.domain.PlaygroundFixtureRun;
import com.fabricmanagement.production.playground.infra.repository.PlaygroundFixtureItemRepository;
import com.fabricmanagement.production.playground.infra.repository.PlaygroundFixtureRunRepository;
import com.fabricmanagement.production.quality.result.app.FiberTestResultService;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The small, coherent playground fibre dataset (FIBER-CATALOG-1 §9), installed through the real
 * domain operations: private source variants, own blends, two synthetic DEMO profiles, batches,
 * measurements (via the normal QC evaluation) and two clearly fictional certificate records.
 *
 * <p>Every private row is bound to a stable per-tenant fixture key; a retry of the same eligible
 * provisioning repairs missing rows individually and never duplicates them. Shared materials are
 * looked up by exact ISO code. Eligibility is enforced by {@code
 * PlaygroundFixtureProvisioningAdapter} before this service is called; this service additionally
 * requires the trusted run marker.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaygroundFiberFixtureService {

  public static final String CO_UNDECLARED = "PG-FIBER-CO-UNDECLARED";
  public static final String CO_VIRGIN = "PG-FIBER-CO-VIRGIN";
  public static final String PES_VIRGIN = "PG-FIBER-PES-VIRGIN";
  public static final String PES_RECYCLED = "PG-FIBER-PES-RECYCLED";
  public static final String CO60_PES40 = "PG-FIBER-CO60-PES40";
  public static final String PES_SOURCE50 = "PG-FIBER-PES-SOURCE50";
  public static final String CO625_PES375 = "PG-FIBER-CO625-PES375";

  static final String PROFILE_ISO_CO = "PG-PROFILE-ISO-CO";
  static final String PROFILE_CO60_PES40 = "PG-PROFILE-CO60-PES40";
  static final String DEMO_ISO_PROFILE_NAME = "DEMO — CO moisture (synthetic criteria)";
  static final String DEMO_BLEND_PROFILE_NAME = "DEMO — CO60/PES40 moisture (synthetic criteria)";
  static final String DEMO_ISSUER = "Demo issuer — not a real certification";
  static final String DEMO_LAB = "Demo lab (illustrative)";

  private static final String SYNTHETIC_REMARK =
      "Playground example (illustrative demo data). Synthetic criteria exercise the software only;"
          + " they are not material tolerances.";

  private final PlaygroundFixtureRunRepository runRepository;
  private final PlaygroundFixtureItemRepository itemRepository;
  private final FiberPlaygroundFixtureService fiberFixtures;
  private final FiberCertificationQueryService certificationQueryService;
  private final BatchService batchService;
  private final StockUnitService stockUnitService;
  private final BatchCertificationService certificationService;
  private final FiberTestResultService testResultService;
  private final Clock clock;

  /** A batch fixture: key, product fixture, code and the optional moisture measurement. */
  private record BatchFixture(String key, String productKey, String code, Double moisture) {}

  /**
   * Installs the fixtures for a tenant whose PENDING marker has the expected origin; a register-
   * first signup records its marker here, inside the onboarding transaction. Returns false when
   * nothing was (or needed to be) installed.
   */
  @Transactional
  public boolean install(UUID tenantId, PlaygroundFixtureOrigin origin, boolean mayOpenRun) {
    Optional<PlaygroundFixtureRun> existing = runRepository.findByTenantIdForUpdate(tenantId);
    PlaygroundFixtureRun run;
    if (existing.isPresent()) {
      run = existing.get();
      if (run.isCompleted() || run.getOrigin() != origin) {
        log.info(
            "Playground fixtures skipped: tenant={}, run={}, origin={}",
            tenantId,
            run.getStatus(),
            run.getOrigin());
        return false;
      }
    } else if (mayOpenRun) {
      run = runRepository.save(PlaygroundFixtureRun.pending(origin));
    } else {
      log.info(
          "Playground fixtures skipped: tenant={} has no trusted provisioning marker", tenantId);
      return false;
    }

    // Bootstrap has no signed-in user. The QC results created below publish approval events whose
    // listener records a quality decision and requires a trusted actor; without one each event
    // failed and stayed an incomplete publication, retried forever. Attribute the fixture work to
    // the system actor, as other system-triggered production writes do.
    TenantContext.TenantSnapshot previous = TenantContext.capture();
    try {
      if (TenantContext.getCurrentUserId() == null) {
        TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
      }
      installDataset(tenantId);
    } finally {
      TenantContext.restore(previous);
    }
    run.complete(clock.instant());
    runRepository.save(run);
    log.info("Playground fibre fixtures installed: tenant={}, origin={}", tenantId, origin);
    return true;
  }

  private void installDataset(UUID tenantId) {
    FiberCatalogReferenceDto co = fiberFixtures.requireSharedPure("CO");
    FiberCatalogReferenceDto pes = fiberFixtures.requireSharedPure("PES");

    Map<String, UUID> products = new LinkedHashMap<>();
    Map<String, UUID> fibers = new LinkedHashMap<>();

    record(tenantId, CO_UNDECLARED, "SHARED_FIBER", co.fiberId());
    fibers.put(CO_UNDECLARED, co.fiberId());
    products.put(CO_UNDECLARED, co.productId());

    variant(
        tenantId,
        CO_VIRGIN,
        "CO",
        MaterialSource.VIRGIN,
        "Cotton — virgin (demo)",
        fibers,
        products);
    variant(
        tenantId,
        PES_VIRGIN,
        "PES",
        MaterialSource.VIRGIN,
        "Polyester — virgin (demo)",
        fibers,
        products);
    variant(
        tenantId,
        PES_RECYCLED,
        "PES",
        MaterialSource.RECYCLED,
        "Polyester — recycled (demo)",
        fibers,
        products);

    blend(
        tenantId,
        CO60_PES40,
        Map.of(co.fiberId(), new BigDecimal("60"), pes.fiberId(), new BigDecimal("40")),
        "Demo blend 60/40 cotton-polyester",
        fibers,
        products);
    blend(
        tenantId,
        PES_SOURCE50,
        Map.of(
            fibers.get(PES_VIRGIN), new BigDecimal("50"),
            fibers.get(PES_RECYCLED), new BigDecimal("50")),
        "Demo blend polyester virgin/recycled",
        fibers,
        products);
    blend(
        tenantId,
        CO625_PES375,
        Map.of(co.fiberId(), new BigDecimal("62.5"), pes.fiberId(), new BigDecimal("37.5")),
        "Demo blend 62.5/37.5 cotton-polyester",
        fibers,
        products);

    ensure(
        tenantId,
        PROFILE_ISO_CO,
        "QUALITY_PROFILE",
        id -> true,
        () ->
            fiberFixtures.ensureDemoMoistureProfile(
                FiberQualityTargetType.ISO_CODE, co.isoCodeId(), DEMO_ISO_PROFILE_NAME, 0, 5, 10));
    ensure(
        tenantId,
        PROFILE_CO60_PES40,
        "QUALITY_PROFILE",
        id -> true,
        () ->
            fiberFixtures.ensureDemoMoistureProfile(
                FiberQualityTargetType.FIBER,
                fibers.get(CO60_PES40),
                DEMO_BLEND_PROFILE_NAME,
                0,
                5,
                10));

    // ISO_CODE CO profile: pass / conditional / reject on shared pure CO.
    // FIBER CO60/PES40 profile: pass / conditional / reject on the exact blend.
    // CO62.5/PES37.5 has no applicable profile: stays pending for manual review.
    // CO-VIRGIN: two fictional certificate examples and one equivalent uncertified batch.
    for (BatchFixture fixture :
        new BatchFixture[] {
          new BatchFixture("PG-BATCH-CO-PASS", CO_UNDECLARED, "PG-FIB-CO-001", 5.0),
          new BatchFixture("PG-BATCH-CO-CONDITIONAL", CO_UNDECLARED, "PG-FIB-CO-002", 7.0),
          new BatchFixture("PG-BATCH-CO-REJECT", CO_UNDECLARED, "PG-FIB-CO-003", 11.0),
          new BatchFixture("PG-BATCH-CO60-PASS", CO60_PES40, "PG-FIB-BL6040-001", 5.0),
          new BatchFixture("PG-BATCH-CO60-CONDITIONAL", CO60_PES40, "PG-FIB-BL6040-002", 7.0),
          new BatchFixture("PG-BATCH-CO60-REJECT", CO60_PES40, "PG-FIB-BL6040-003", 11.0),
          new BatchFixture("PG-BATCH-CO625-PENDING", CO625_PES375, "PG-FIB-BL625-001", 5.0),
          new BatchFixture("PG-BATCH-COV-GOTS", CO_VIRGIN, "PG-FIB-COV-001", null),
          new BatchFixture("PG-BATCH-COV-OEKO", CO_VIRGIN, "PG-FIB-COV-002", null),
          new BatchFixture("PG-BATCH-COV-UNCERTIFIED", CO_VIRGIN, "PG-FIB-COV-003", null)
        }) {
      UUID batchId = batch(tenantId, fixture, products.get(fixture.productKey()));
      if (fixture.moisture() != null) {
        measurement(tenantId, fixture.key() + "-QC", batchId, fixture.moisture());
      }
    }
    certificate(tenantId, "PG-CERT-COV-GOTS", "PG-BATCH-COV-GOTS", "GOTS", "DEMO-GOTS-0001");
    certificate(
        tenantId, "PG-CERT-COV-OEKO", "PG-BATCH-COV-OEKO", "OEKO_TEX_100", "DEMO-OEKO-0001");
  }

  private void variant(
      UUID tenantId,
      String key,
      String isoCode,
      MaterialSource source,
      String name,
      Map<String, UUID> fibers,
      Map<String, UUID> products) {
    var fiber = fiberFixtures.ensureSourceVariant(isoCode, source, name);
    record(tenantId, key, "FIBER", fiber.getId());
    fibers.put(key, fiber.getId());
    products.put(key, fiber.getProductId());
  }

  private void blend(
      UUID tenantId,
      String key,
      Map<UUID, BigDecimal> composition,
      String name,
      Map<String, UUID> fibers,
      Map<String, UUID> products) {
    FiberDto fiber = fiberFixtures.ensureBlend(composition, name);
    record(tenantId, key, "FIBER", fiber.getId());
    fibers.put(key, fiber.getId());
    products.put(key, fiber.getProductId());
  }

  private UUID batch(UUID tenantId, BatchFixture fixture, UUID productId) {
    return ensure(
        tenantId,
        fixture.key(),
        "BATCH",
        id -> batchService.getById(id).isPresent(),
        () ->
            batchService
                .findByBatchCode(fixture.code())
                .map(BatchDto::getId)
                .orElseGet(() -> createBaledBatch(fixture, productId)));
  }

  /**
   * A fixture lot is two 250 kg bales, not a bare 500 kg figure. The approved or rejected lab
   * result below publishes an event whose listener records a lot-level quality decision, and a
   * decision needs physical units to release or reject: on a lot without pieces it failed with "no
   * eligible StockUnits" and the publication stayed incomplete, retried on every restart. With
   * bales the fixtures go through the normal QC flow the catalogue ticket asks for.
   */
  private UUID createBaledBatch(BatchFixture fixture, UUID productId) {
    UUID batchId =
        batchService
            .create(
                CreateBatchRequest.builder()
                    .productId(productId)
                    .productType(ProductType.FIBER)
                    .batchCode(fixture.code())
                    .quantity(new BigDecimal("500.00"))
                    .unit("KG")
                    .sourceType(BatchSourceType.INITIAL_STOCK)
                    .remarks(SYNTHETIC_REMARK)
                    .build())
            .getId();
    for (int bale = 1; bale <= 2; bale++) {
      stockUnitService.create(
          batchId,
          ProductType.FIBER,
          fixture.code() + "-B" + bale,
          null,
          PackageType.BALE,
          new BigDecimal("250.000"),
          null,
          "KG",
          null,
          null,
          null,
          StockUnitSourceType.PRODUCTION,
          batchId);
    }
    return batchId;
  }

  private void measurement(UUID tenantId, String key, UUID batchId, double moisture) {
    ensure(
        tenantId,
        key,
        "FIBER_TEST_RESULT",
        id -> testResultService.getById(id).isPresent(),
        () ->
            testResultService.getByBatchId(batchId).stream()
                .filter(existing -> DEMO_LAB.equals(existing.getTestLab()))
                .map(FiberTestResultDto::getId)
                .findFirst()
                .orElseGet(
                    () ->
                        testResultService
                            .create(
                                CreateFiberTestResultRequest.builder()
                                    .batchId(batchId)
                                    .testDate(clock.instant())
                                    .testType("LABORATORY")
                                    .moisturePercent(moisture)
                                    .testLab(DEMO_LAB)
                                    .remarks(SYNTHETIC_REMARK)
                                    .build())
                            .getId()));
  }

  /**
   * Clearly fictional supplier-scope certificate example: DEMO number, demo issuer, no document. It
   * is SCOPE evidence (not a transaction certificate) and keeps the normal missing-document /
   * insufficient-evidence behaviour.
   */
  private void certificate(
      UUID tenantId, String key, String batchKey, String schemeCode, String certNumber) {
    UUID batchId =
        itemRepository
            .findByTenantIdAndFixtureKey(tenantId, batchKey)
            .map(PlaygroundFixtureItem::getEntityId)
            .orElseThrow(() -> new IllegalStateException("Missing fixture batch " + batchKey));
    FiberCertification scheme =
        certificationQueryService
            .findActiveEntityByCode(schemeCode)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Shared certification scheme " + schemeCode + " is not published"));
    LocalDate today = LocalDate.now(clock);
    ensure(
        tenantId,
        key,
        "BATCH_CERTIFICATION",
        id ->
            certificationService.findByBatchId(batchId).stream()
                .anyMatch(existing -> id.equals(existing.getId())),
        () ->
            certificationService.findByBatchId(batchId).stream()
                .filter(existing -> certNumber.equals(existing.getCertNumber()))
                .map(BatchCertificationDto::getId)
                .findFirst()
                .orElseGet(() -> addCertificate(batchId, scheme, certNumber, today)));
  }

  private UUID addCertificate(
      UUID batchId, FiberCertification scheme, String certNumber, LocalDate today) {
    AddBatchCertificationRequest request =
        AddBatchCertificationRequest.builder()
            .certificationId(scheme.getId())
            .scope(BatchCertificationScope.SUPPLIER)
            .certificateKind(BatchCertificateKind.SCOPE)
            .certNumber(certNumber)
            .validFrom(today.minusMonths(1))
            .validUntil(today.plusMonths(11))
            .certifyingBodyRef(DEMO_ISSUER)
            .remarks(
                "Fictional playground example; not a real certification claim and not a"
                    + " transaction certificate.")
            .build();
    return certificationService.add(batchId, request).getData().getId();
  }

  private void record(UUID tenantId, String key, String entityType, UUID entityId) {
    Optional<PlaygroundFixtureItem> item =
        itemRepository.findByTenantIdAndFixtureKey(tenantId, key);
    if (item.isEmpty()) {
      itemRepository.save(PlaygroundFixtureItem.of(key, entityType, entityId));
    } else if (!entityId.equals(item.get().getEntityId())) {
      item.get().rebind(entityId);
      itemRepository.save(item.get());
    }
  }

  private UUID ensure(
      UUID tenantId,
      String key,
      String entityType,
      Predicate<UUID> stillExists,
      Supplier<UUID> create) {
    Optional<PlaygroundFixtureItem> item =
        itemRepository.findByTenantIdAndFixtureKey(tenantId, key);
    if (item.isPresent() && stillExists.test(item.get().getEntityId())) {
      return item.get().getEntityId();
    }
    UUID id = create.get();
    record(tenantId, key, entityType, id);
    return id;
  }
}
