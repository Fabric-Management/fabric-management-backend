package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.app.RoleService;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.domain.ContactType;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.product.color.app.ColorPartnerRefQueryService;
import com.fabricmanagement.product.color.app.ColorPartnerRefService;
import com.fabricmanagement.product.color.app.ColorService;
import com.fabricmanagement.product.color.domain.Color;
import com.fabricmanagement.product.color.domain.ColorCardSpec;
import com.fabricmanagement.product.color.domain.ColorFamily;
import com.fabricmanagement.product.color.domain.ColorPartnerCode;
import com.fabricmanagement.product.color.domain.ColorPartnerRef;
import com.fabricmanagement.product.color.domain.ColorStandardStatus;
import com.fabricmanagement.product.color.domain.ColorType;
import com.fabricmanagement.product.color.domain.DeltaEFormula;
import com.fabricmanagement.product.color.domain.LabIlluminant;
import com.fabricmanagement.product.color.domain.LabObserver;
import com.fabricmanagement.product.color.domain.PantoneSystem;
import com.fabricmanagement.product.color.domain.PartnerRole;
import com.fabricmanagement.product.color.dto.AddColorPartnerCodeRequest;
import com.fabricmanagement.product.color.dto.ColorPartnerCodeInput;
import com.fabricmanagement.product.color.dto.CreateColorPartnerRefRequest;
import com.fabricmanagement.product.core.api.facade.ProductFacade;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.CreateProductRequest;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.qualitygrade.app.QualityGradeService;
import com.fabricmanagement.product.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.production.core.batch.app.BatchService;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.dto.BatchDto;
import com.fabricmanagement.production.core.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.core.batch.dto.ReserveRequest;
import com.fabricmanagement.production.core.stockunit.app.StockUnitService;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.sales.ownership.app.CustomerAccountTeamService;
import com.fabricmanagement.sales.pricing.app.DiscountPolicyService;
import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.quote.api.QuoteCreateRequest;
import com.fabricmanagement.sales.quote.app.QuoteService;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSelectionRequest;
import com.fabricmanagement.sales.salesproduct.app.SalesProductService;
import com.fabricmanagement.sales.salesproduct.dto.CreateSalesProductRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the playground sales/ATP demo dataset (DEMO-SALES-1) for the "Pennine Mills Ltd" scenario:
 * quality grades, colour cards (family, type, one approved shade standard with a synthetic target
 * Lab, PFD and greige undyed cards), partner colour codes (customer primary + previous-season
 * alias, supplier code), products with GBP catalogue entries, dye lots with rolls/cartons —
 * stocked, hard-reserved and a deliberately stockless colour — a competing marketer quote whose
 * soft lot-quantity intents shrink Navy free stock, one hard {@code BatchReservation}, and a
 * pre-filled draft quote — so the quote picker and the order form exercise every colour/stock
 * behaviour on realistic British data.
 *
 * <p>Runs post-clone inside the cloned tenant's context and writes via application services so real
 * invariants apply. Replays are safe twice over: the whole run is one transaction, and the demo
 * customer marker short-circuits a completed run; inside a run, colours, grades, products, lots and
 * partner references are looked up by their stable keys before they are created, so rows that
 * already exist (for example a colour card the tenant entered by hand) are reused, never
 * duplicated.
 *
 * <p>Callers must bind the tenant ({@link TenantContext}) BEFORE calling {@link #seedFor}: its
 * transaction acquires the connection — and row-level security binds {@code app.current_tenant} —
 * before the method body runs, so the context set inside only covers Java-side lookups.
 *
 * <p>Runs in its OWN transaction ({@link Propagation#REQUIRES_NEW}) and is invoked from {@link
 * DemoTransactionSeeder} inside a try/catch — the exact {@link SalesDemoSeeder} pattern. Signup and
 * onboarding call the seeding chain from inside their own transaction; without this boundary a
 * single failed INSERT here aborts the shared Postgres transaction ("current transaction is
 * aborted") and turns the whole signup into a 500 even though the exception itself is caught. With
 * it, a failure rolls back only the demo dataset and can never break playground initialisation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SalesQuoteDemoSeeder {

  static final String CUSTOMER_ALBION = "Albion Apparel Ltd";

  /**
   * Quote-number stems per the DEMO-SALES-1 ticket. {@code sales.quote.quote_number} carries a
   * GLOBAL unique constraint (not tenant-scoped — see V001 + the tenant-scoped-uniques migration,
   * which does not cover it), so each playground tenant appends its own short suffix to avoid
   * cross-tenant collisions.
   */
  static final String COMPETING_QUOTE_NUMBER_STEM = "Q-2026-1041";

  static final String DRAFT_QUOTE_NUMBER_STEM = "Q-2026-1042";
  static final String MARKETER_FIRST_NAME = "Emma";
  static final String MARKETER_LAST_NAME = "Whitfield";
  static final String MARKETER_EMAIL = "emma.whitfield@nexusfabrics.com";

  /**
   * Batch-header unit for fabric length lots. {@code chk_batch_unit} admits {@code M} since
   * V20260719120000, and {@link
   * com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService} reads {@code MT} as
   * a metric tonne: a fabric lot booked in {@code MT} fails the first coverage check with {@code
   * BatchUnitMeasureMismatchException} and rolls this whole seed back, which is exactly how the
   * playground lost its colours. Fabric lots are therefore booked in metres, matching the rolls'
   * {@code StockUnit.lengthUnit = "M"}.
   */
  static final String BATCH_UNIT_METRES = "M";

  static final String PRODUCT_GABARDINE = "PM-1453 Gabardine 155cm 190gsm";
  static final String PRODUCT_COMBED_YARN = "PM-3001 Combed Yarn Ne 30/1";
  static final String PRODUCT_RAW_COTTON = "PM-9001 Raw Cotton";

  /** Every demo colour is a synthetic standard; the note makes that visible on the card. */
  static final String SYNTHETIC_STANDARD_NOTE =
      "Demo colour — synthetic target values, not a real dye-house standard.";

  static final ColorCardSpec NAVY_SPEC =
      ColorCardSpec.builder()
          .code("NAVY-01")
          .name("Navy")
          .colorHex("#1F2A44")
          .colorType(ColorType.DYED)
          .colorFamily(ColorFamily.BLUE)
          .pantoneCode("19-4024")
          .pantoneSystem(PantoneSystem.TCX)
          .targetLabL(new BigDecimal("20.50"))
          .targetLabA(new BigDecimal("1.20"))
          .targetLabB(new BigDecimal("-14.80"))
          .targetLabIlluminant(LabIlluminant.D65)
          .targetLabObserver(LabObserver.DEG_10)
          .deltaETolerance(new BigDecimal("1.00"))
          .deltaEFormula(DeltaEFormula.CMC_2_1)
          .notes(SYNTHETIC_STANDARD_NOTE + " Approved: shade standard frozen.")
          .build();

  static final ColorCardSpec ECRU_SPEC =
      ColorCardSpec.builder()
          .code("ECRU-02")
          .name("Ecru")
          .colorHex("#F0EAD6")
          .colorType(ColorType.DYED)
          .colorFamily(ColorFamily.BEIGE)
          .targetLabL(new BigDecimal("91.30"))
          .targetLabA(new BigDecimal("0.60"))
          .targetLabB(new BigDecimal("9.40"))
          .targetLabIlluminant(LabIlluminant.D65)
          .targetLabObserver(LabObserver.DEG_10)
          .deltaETolerance(new BigDecimal("1.50"))
          .deltaEFormula(DeltaEFormula.CIEDE2000)
          .notes(
              SYNTHETIC_STANDARD_NOTE + " Draft: target Lab entered, awaiting internal sign-off.")
          .build();

  static final ColorCardSpec CHARCOAL_SPEC =
      ColorCardSpec.builder()
          .code("CHAR-03")
          .name("Charcoal")
          .colorHex("#36454F")
          .colorType(ColorType.DYED)
          .colorFamily(ColorFamily.GREY)
          .notes(SYNTHETIC_STANDARD_NOTE + " Draft: screen approximation only, no target yet.")
          .build();

  /** Undyed cards carry no hex, Pantone, target Lab or tolerance (colour card invariant). */
  static final ColorCardSpec PFD_SPEC =
      ColorCardSpec.builder()
          .code("PFD-00")
          .name("Prepared For Dyeing")
          .colorType(ColorType.PFD)
          .colorFamily(ColorFamily.WHITE)
          .notes("Undyed, prepared-for-dyeing stock. No shade standard applies.")
          .build();

  static final ColorCardSpec GREIGE_SPEC =
      ColorCardSpec.builder()
          .code("GREIGE-00")
          .name("Greige (loomstate)")
          .colorType(ColorType.GREIGE)
          .notes("Loomstate stock straight off the loom. No shade standard applies.")
          .build();

  /** Rust deliberately gets NO stock — the passive-but-selectable colour case. */
  static final ColorCardSpec RUST_SPEC =
      ColorCardSpec.builder()
          .code("RUST-04")
          .name("Rust")
          .colorHex("#B7410E")
          .colorType(ColorType.DYED)
          .colorFamily(ColorFamily.ORANGE)
          .notes(SYNTHETIC_STANDARD_NOTE + " Catalogue colour with no stock on hand.")
          .build();

  static final ColorCardSpec INDIGO_SPEC =
      ColorCardSpec.builder()
          .code("IND-05")
          .name("Indigo")
          .colorHex("#2E3A87")
          // DYED, not YARN_DYED: that type means a fabric woven or knitted from pre-dyed yarn.
          // This card is the shade of a package-dyed yarn lot itself.
          .colorType(ColorType.DYED)
          .colorFamily(ColorFamily.BLUE)
          .pantoneCode("19-3933")
          .pantoneSystem(PantoneSystem.TCX)
          .targetLabL(new BigDecimal("24.00"))
          .targetLabA(new BigDecimal("6.50"))
          .targetLabB(new BigDecimal("-28.00"))
          .targetLabIlluminant(LabIlluminant.D65)
          .targetLabObserver(LabObserver.DEG_10)
          .deltaETolerance(new BigDecimal("1.20"))
          .deltaEFormula(DeltaEFormula.CMC_2_1)
          .notes(SYNTHETIC_STANDARD_NOTE + " Approved standard for the package-dyed yarn lot.")
          .build();

  static final List<ColorCardSpec> COLOUR_SPECS =
      List.of(NAVY_SPEC, ECRU_SPEC, CHARCOAL_SPEC, PFD_SPEC, GREIGE_SPEC, RUST_SPEC, INDIGO_SPEC);

  static final ColorPartnerCodeInput ALBION_NAVY_PRIMARY =
      new ColorPartnerCodeInput("ALB-NVY-26", "Albion Navy");
  static final ColorPartnerCodeInput ALBION_NAVY_PREVIOUS_SEASON =
      new ColorPartnerCodeInput("SS26-NAVY", "Navy (SS26 season code)");
  static final ColorPartnerCodeInput ALBION_ECRU_PRIMARY =
      new ColorPartnerCodeInput("ALB-ECR-26", "Albion Ecru");
  static final ColorPartnerCodeInput AEGEAN_INDIGO_PRIMARY =
      new ColorPartnerCodeInput("AYM-IND-2201", "Indigo 22");

  private static final String CURRENCY = "GBP";
  private static final String QUOTE_MODULE_TYPE = "FABRIC";
  private static final String GRADING_REASON = "Initial grading (demo seed)";
  private static final String GABARDINE_LIST_PRICE = "6.80";
  private static final String GABARDINE_OFFERED_PRICE = "6.50";

  private final TradingPartnerService tradingPartnerService;
  private final ProductFacade productFacade;
  private final QualityGradeService qualityGradeService;
  private final ColorService colorService;
  private final ColorPartnerRefService colorPartnerRefService;
  private final ColorPartnerRefQueryService colorPartnerRefQueryService;
  private final BatchService batchService;
  private final StockUnitService stockUnitService;
  private final SalesProductService salesProductService;
  private final DiscountPolicyService discountPolicyService;
  private final ExchangeRateService exchangeRateService;
  private final QuoteService quoteService;
  private final CustomerAccountTeamService customerAccountTeamService;
  private final UserRepository userRepository;
  private final UserCreationService userCreationService;
  private final RoleService roleService;
  private final DepartmentRepository departmentRepository;
  private final OrganizationService organizationService;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void seedFor(UUID tenantId) {
    TenantContext.TenantSnapshot previous = TenantContext.capture();
    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentUserId(SystemUser.ID);

      if (!tradingPartnerService.searchByName(tenantId, CUSTOMER_ALBION).isEmpty()) {
        log.info("Sales quote demo data already exists for tenant: {}. Skipping.", tenantId);
        return;
      }

      LocalDate today = LocalDate.now(clock);

      // ── Master data ──
      GradeTrio fabricGrades = seedGradeTrio(ProductType.FABRIC);
      GradeTrio yarnGrades = seedGradeTrio(ProductType.YARN);
      seedGradeTrio(ProductType.FIBER);

      // Colour cards: two approved dyed standards (Navy for fabric, Indigo for package-dyed yarn),
      // draft cards with and without a target, two undyed cards, and a stockless catalogue colour.
      Color navy = ensureApproved(ensureColour(NAVY_SPEC));
      Color ecru = ensureColour(ECRU_SPEC);
      ensureColour(CHARCOAL_SPEC);
      Color pfd = ensureColour(PFD_SPEC);
      ensureColour(GREIGE_SPEC);
      ensureColour(RUST_SPEC);
      Color indigo = ensureApproved(ensureColour(INDIGO_SPEC));

      ProductDto gabardine =
          ensureProduct(ProductType.FABRIC, "M", PRODUCT_GABARDINE, GABARDINE_LIST_PRICE);
      ProductDto combedYarn = ensureProduct(ProductType.YARN, "KG", PRODUCT_COMBED_YARN, "4.20");
      ProductDto rawCotton = ensureProduct(ProductType.FIBER, "KG", PRODUCT_RAW_COTTON, "1.85");

      ensureDiscountPolicy();
      // Reporting currency for demo tenants is USD (FinanceDemoSeeder); GBP quotes need this rate.
      exchangeRateService.saveRate(
          "USD", CURRENCY, new BigDecimal("0.79"), today, ExchangeRateSource.MANUAL);

      // ── Lots and pieces (per the DEMO-SALES-1 table) ──
      // Fabric lot headers are booked in metres (BATCH_UNIT_METRES); metre detail also lives on the
      // rolls (StockUnit.lengthUnit = "M"), which is what the lot picker surfaces for piece-backed
      // length lots. Every lot is looked up by its code first, so a replay never books it twice.
      BatchDto lot24011 =
          ensureLot(
              gabardine,
              "LOT-24011",
              "2000",
              BATCH_UNIT_METRES,
              navy.getId(),
              "Main Navy dye lot, spring run",
              id -> addRolls(id, "24011", 16, "125", "36.800", fabricGrades.first().getId()));

      BatchDto lot24012 =
          ensureLot(
              gabardine,
              "LOT-24012",
              "3000",
              BATCH_UNIT_METRES,
              navy.getId(),
              "Second Navy dye lot — shade may vary",
              id -> addRolls(id, "24012", 24, "125", "36.800", fabricGrades.first().getId()));

      ensureLot(
          gabardine,
          "LOT-23087",
          "290",
          BATCH_UNIT_METRES,
          navy.getId(),
          "Remnant lot — close it out",
          id -> {
            addRoll(id, "23087", 1, "100", "29.500", fabricGrades.first().getId());
            addRoll(id, "23087", 2, "95", "28.000", fabricGrades.first().getId());
            addRoll(id, "23087", 3, "95", "28.000", fabricGrades.first().getId());
          });

      BatchDto lot24020 =
          ensureLot(
              gabardine,
              "LOT-24020",
              "450",
              BATCH_UNIT_METRES,
              ecru.getId(),
              "Second Quality Ecru — piece-backed demo lot",
              id -> addRolls(id, "24020", 18, "25", "25.000", fabricGrades.second().getId()));

      ensureLot(
          combedYarn,
          "LOT-24031",
          "1200",
          "KG",
          pfd.getId(),
          "Combed yarn, prepared for dyeing",
          id -> addCartons(id, "24031", 48, "25.000", yarnGrades.first().getId()));

      // Coloured yarn stock: a yarn-dyed Indigo lot in cartons, fully free.
      ensureLot(
          combedYarn,
          "LOT-24035",
          "600",
          "KG",
          indigo.getId(),
          "Indigo yarn-dyed lot — package dyed, 24 cartons",
          id -> addCartons(id, "24035", 24, "25.000", yarnGrades.first().getId()));

      // Raw cotton skips the colour axis entirely — fibre cascade has no Colour step.
      ensureLot(rawCotton, "LOT-24040", "5000", "KG", null, "Raw cotton, bulk store", null);

      // Waste-grade stock exists but must stay invisible to the picker (negative test).
      ensureLot(
          gabardine,
          "LOT-23050",
          "120",
          BATCH_UNIT_METRES,
          navy.getId(),
          "Waste grade — not saleable",
          id -> {
            addRoll(id, "23050", 1, "60", "17.700", fabricGrades.waste().getId());
            addRoll(id, "23050", 2, "60", "17.700", fabricGrades.waste().getId());
          });

      // ── Actors ──
      UUID emmaId = ensureMarketer(tenantId);
      TradingPartnerDto albion = createAlbionCustomer(tenantId, emmaId);
      UUID draftOwnerId = resolveDraftOwner(tenantId, emmaId);
      if (!draftOwnerId.equals(emmaId)) {
        customerAccountTeamService.addMember(tenantId, albion.getId(), draftOwnerId);
      }

      // ── Partner colour codes (ADR-0009) ──
      // Albion names Navy by a current primary code and still quotes last season's code; the yarn
      // supplier from the procurement demo has its own code for Indigo.
      ensurePartnerCodes(
          navy,
          albion.getId(),
          PartnerRole.CUSTOMER,
          ALBION_NAVY_PRIMARY,
          List.of(ALBION_NAVY_PREVIOUS_SEASON));
      ensurePartnerCodes(
          ecru, albion.getId(), PartnerRole.CUSTOMER, ALBION_ECRU_PRIMARY, List.of());
      UUID aegean = ensureSupplier(tenantId, ProcurementDemoSeeder.SUPPLIER_AEGEAN);
      ensurePartnerCodes(indigo, aegean, PartnerRole.SUPPLIER, AEGEAN_INDIGO_PRIMARY, List.of());

      // ── Emma's competing open quote: soft intents shrink Navy free stock ──
      Quote emmaQuote =
          createQuote(
              albion.getId(),
              emmaId,
              quoteNumber(COMPETING_QUOTE_NUMBER_STEM, tenantId),
              today.plusDays(14),
              "Navy gabardine for the spring collection — awaiting customer confirmation");
      // Each line's selected-lot quantity equals the line requested quantity, so the seed passes
      // the same invariant real users hit; QuoteService writes the intents through
      // BatchLotQuantityIntentPort.replaceIntents with Emma's quote/line ids.
      addLotBackedLine(
          emmaQuote.getId(),
          gabardine.getId(),
          fabricGrades.first().getId(),
          navy.getId(),
          lot24011.getId(),
          "1500");
      addLotBackedLine(
          emmaQuote.getId(),
          gabardine.getId(),
          fabricGrades.first().getId(),
          navy.getId(),
          lot24012.getId(),
          "2000");

      // ── Hard reservation: an order in progress holds 200 m of the Ecru bulk lot ──
      batchService.reserve(
          lot24020.getId(),
          ReserveRequest.builder()
              .quantity(new BigDecimal("200"))
              .referenceType("SALES_ORDER")
              .remarks("Order in progress — hard reservation (demo)")
              .build());

      // ── Draft quote for the demo user, one line pre-filled, no lots yet ──
      Quote draftQuote =
          createQuote(
              albion.getId(),
              draftOwnerId,
              quoteNumber(DRAFT_QUOTE_NUMBER_STEM, tenantId),
              today.plusDays(30),
              "Draft — open the lot picker on the Gabardine line to continue");
      addFreeEntryLine(
          draftQuote.getId(), gabardine.getId(), fabricGrades.first().getId(), navy.getId());

      log.info("Successfully provisioned sales quote demo data for tenant: {}", tenantId);
    } finally {
      // Failures propagate to DemoTransactionSeeder's try/catch so THIS transaction (REQUIRES_NEW)
      // rolls back cleanly. Swallowing here would commit a rollback-only transaction instead.
      TenantContext.restore(previous);
    }
  }

  // ── Master data helpers ─────────────────────────────────────────────────────

  private GradeTrio seedGradeTrio(ProductType productType) {
    QualityGrade first =
        ensureGrade(productType, "1ST", "First Quality", 1, "1.000", true, false, "#22C55E", true);
    QualityGrade second =
        ensureGrade(
            productType, "2ND", "Second Quality", 2, "0.550", true, false, "#EAB308", false);
    QualityGrade waste =
        ensureGrade(productType, "WASTE", "Waste", 3, "0.100", false, true, "#EF4444", false);
    return new GradeTrio(first, second, waste);
  }

  private QualityGrade ensureGrade(
      ProductType productType,
      String code,
      String name,
      int rank,
      String priceFactor,
      boolean saleable,
      boolean requiresApproval,
      String colorHex,
      boolean isDefault) {
    return qualityGradeService.findByProductType(productType).stream()
        .filter(grade -> code.equalsIgnoreCase(grade.getCode()))
        .findFirst()
        .orElseGet(
            () ->
                qualityGradeService.create(
                    productType,
                    code,
                    name,
                    rank,
                    new BigDecimal(priceFactor),
                    saleable,
                    requiresApproval,
                    colorHex,
                    isDefault));
  }

  private Color ensureColour(ColorCardSpec spec) {
    return colorService.list(true).stream()
        .filter(colour -> spec.code().equalsIgnoreCase(colour.getCode()))
        .findFirst()
        .orElseGet(() -> colorService.create(spec));
  }

  /** Freezes the card's shade standard; {@code approve} is idempotent on an approved card. */
  private Color ensureApproved(Color colour) {
    if (colour.getStandardStatus() == ColorStandardStatus.APPROVED) {
      return colour;
    }
    return colorService.approve(colour.getId());
  }

  /**
   * Products carry no tenant code, so the catalogue name is their stable demo key: an active
   * catalogue entry with that name means the product already exists.
   */
  private ProductDto ensureProduct(
      ProductType productType, String unit, String catalogueName, String listPrice) {
    UUID tenantId = TenantContext.requireTenantId();
    Optional<ProductDto> existing =
        salesProductService.getActiveCatalogForModule(QUOTE_MODULE_TYPE).stream()
            .filter(entry -> catalogueName.equals(entry.getProductName()))
            .findFirst()
            .flatMap(entry -> productFacade.findById(tenantId, entry.getProductId()));
    if (existing.isPresent()) {
      return existing.get();
    }
    ProductDto product =
        productFacade.createProduct(
            CreateProductRequest.builder().productType(productType).unit(unit).build());
    catalogueEntry(product, catalogueName, listPrice);
    return product;
  }

  private void catalogueEntry(ProductDto product, String productName, String listPrice) {
    salesProductService.createEntry(
        new CreateSalesProductRequest(
            product.getId(),
            productName,
            QUOTE_MODULE_TYPE,
            new BigDecimal(listPrice),
            CURRENCY,
            null,
            null,
            null,
            null,
            null,
            null));
  }

  /**
   * Asks, never catches: {@code getActivePolicy} throws from inside a joined transactional method,
   * and a RuntimeException leaving a {@code @Transactional} boundary marks this seed's transaction
   * rollback-only even when the caller swallows it — the whole seed then rolls back at commit.
   */
  private void ensureDiscountPolicy() {
    if (discountPolicyService.findActivePolicy(QUOTE_MODULE_TYPE).isPresent()) {
      return;
    }
    DiscountPolicy policy = new DiscountPolicy();
    policy.setModuleType(QUOTE_MODULE_TYPE);
    policy.setBaseDiscountLimit(new BigDecimal("0.1000"));
    policy.setMinProfitMargin(new BigDecimal("0.0500"));
    policy.setRequireManagerAbove(new BigDecimal("0.1500"));
    discountPolicyService.savePolicy(policy);
  }

  // ── Lot helpers ─────────────────────────────────────────────────────────────

  /**
   * Books a lot once: an existing batch with the same code is returned untouched (its pieces and QC
   * release happened when it was first created). A {@code null} pieces callback books a scalar lot
   * that stays pending with no physical units.
   */
  private BatchDto ensureLot(
      ProductDto product,
      String batchCode,
      String quantity,
      String unit,
      UUID colorId,
      String remarks,
      Consumer<UUID> pieces) {
    Optional<BatchDto> existing = batchService.findByBatchCode(batchCode);
    if (existing.isPresent()) {
      return existing.get();
    }
    BatchDto batch = pendingBatch(product, batchCode, quantity, unit, colorId, remarks);
    if (pieces != null) {
      pieces.accept(batch.getId());
      releasePieceBackedBatch(batch.getId());
    }
    return batch;
  }

  private BatchDto pendingBatch(
      ProductDto product,
      String batchCode,
      String quantity,
      String unit,
      UUID colorId,
      String remarks) {
    BatchDto batch =
        batchService.create(
            CreateBatchRequest.builder()
                .productId(product.getId())
                .productType(product.getProductType())
                .batchCode(batchCode)
                .colorId(colorId)
                .quantity(new BigDecimal(quantity))
                .unit(unit)
                .sourceType(BatchSourceType.INITIAL_STOCK)
                .remarks(remarks)
                .build());
    return batch;
  }

  private void releasePieceBackedBatch(UUID batchId) {
    // Demo stock follows the same immutable QC release path as operational stock.
    batchService.releaseFromQc(batchId);
  }

  private void addRolls(
      UUID batchId,
      String lotDigits,
      int count,
      String lengthMetres,
      String weightKg,
      UUID gradeId) {
    for (int index = 1; index <= count; index++) {
      addRoll(batchId, lotDigits, index, lengthMetres, weightKg, gradeId);
    }
  }

  private void addRoll(
      UUID batchId,
      String lotDigits,
      int index,
      String lengthMetres,
      String weightKg,
      UUID gradeId) {
    StockUnit unit =
        stockUnitService.create(
            batchId,
            ProductType.FABRIC,
            barcode(lotDigits, index),
            null,
            PackageType.ROLL,
            new BigDecimal(weightKg),
            null,
            "KG",
            new BigDecimal(lengthMetres),
            "M",
            null,
            StockUnitSourceType.PRODUCTION,
            batchId);
    stockUnitService.changeGrade(unit.getId(), gradeId, GRADING_REASON, null);
  }

  private void addCartons(
      UUID batchId, String lotDigits, int count, String weightKg, UUID gradeId) {
    for (int index = 1; index <= count; index++) {
      StockUnit unit =
          stockUnitService.create(
              batchId,
              ProductType.YARN,
              barcode(lotDigits, index),
              null,
              PackageType.CARTON,
              new BigDecimal(weightKg),
              null,
              "KG",
              null,
              null,
              null,
              StockUnitSourceType.PRODUCTION,
              batchId);
      stockUnitService.changeGrade(unit.getId(), gradeId, GRADING_REASON, null);
    }
  }

  private String barcode(String lotDigits, int index) {
    return String.format("PM-%s-%02d", lotDigits, index);
  }

  // ── Partner colour code helpers ─────────────────────────────────────────────

  /**
   * One relationship per (colour, partner, role) with a primary code, plus aliases the partner
   * still uses. Existing relationships and codes are matched by their canonical key and left as
   * they are.
   */
  private void ensurePartnerCodes(
      Color colour,
      UUID partnerId,
      PartnerRole role,
      ColorPartnerCodeInput primary,
      List<ColorPartnerCodeInput> aliases) {
    ColorPartnerRef ref =
        colorPartnerRefQueryService.list(colour.getId(), Pageable.unpaged()).stream()
            .filter(candidate -> partnerId.equals(candidate.getPartnerId()))
            .filter(candidate -> candidate.getRole() == role)
            .findFirst()
            .orElseGet(
                () ->
                    colorPartnerRefService.create(
                        colour.getId(),
                        new CreateColorPartnerRefRequest(partnerId, role, null, primary)));
    for (ColorPartnerCodeInput alias : aliases) {
      String key = ColorPartnerCode.keyOf(alias.externalCode());
      boolean present =
          ref.getCodes().stream().anyMatch(code -> key.equals(code.getExternalCodeKey()));
      if (!present) {
        colorPartnerRefService.addCode(
            colour.getId(),
            ref.getId(),
            new AddColorPartnerCodeRequest(alias.externalCode(), alias.externalName()));
      }
    }
  }

  /**
   * The yarn supplier normally comes from the procurement demo; when that demo did not run (it
   * needs its own persona), the supplier is created here so the supplier colour code always has a
   * partner to hang off.
   */
  private UUID ensureSupplier(UUID tenantId, String companyName) {
    return tradingPartnerService.searchByName(tenantId, companyName).stream()
        .map(TradingPartnerDto::getId)
        .findFirst()
        .orElseGet(
            () -> {
              CreateTradingPartnerRequest req = new CreateTradingPartnerRequest();
              req.setCompanyName(companyName);
              req.setCustomName(companyName);
              // Same tax id the procurement demo would use, so the two seeds never disagree.
              req.setTaxId("TR-P2P-AEG-" + tenantSuffix(tenantId));
              req.setCountry("TUR");
              req.setPartnerType(PartnerType.SUPPLIER);
              req.setRelationshipMeta(
                  Map.of("payment_terms", "NET45", "notes", "Combed yarn and fiber supplier"));
              return tradingPartnerService.createPartner(req, null).getId();
            });
  }

  // ── Actor helpers ───────────────────────────────────────────────────────────

  private TradingPartnerDto createAlbionCustomer(UUID tenantId, UUID acquiredById) {
    CreateTradingPartnerRequest req = new CreateTradingPartnerRequest();
    req.setCompanyName(CUSTOMER_ALBION);
    req.setCustomName(CUSTOMER_ALBION);
    req.setTaxId("GB-ALB-" + tenantSuffix(tenantId));
    req.setCountry("GBR");
    req.setPartnerType(PartnerType.CUSTOMER);
    req.setRelationshipMeta(
        Map.of(
            "payment_terms", "NET30",
            "contact_email", "buying@albionapparel.co.uk",
            "notes", "Demo customer for sales quotes"));
    return tradingPartnerService.createPartner(req, acquiredById);
  }

  private UUID ensureMarketer(UUID tenantId) {
    return userRepository
        .findFirstByTenantIdAndFirstNameAndLastNameAndIsActiveTrue(
            tenantId, MARKETER_FIRST_NAME, MARKETER_LAST_NAME)
        .map(User::getId)
        .orElseGet(() -> createMarketer(tenantId));
  }

  /**
   * Creates the competing-marketer persona through the same services {@link UserSeeder} uses. No
   * credential is provisioned — playground access works via impersonation, matching how cloned
   * persona users behave (TenantClonerService intentionally skips auth users).
   */
  private UUID createMarketer(UUID tenantId) {
    OrganizationDto rootOrg =
        organizationService
            .getRootOrganization()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Root organisation missing — cannot seed demo marketer"));
    Role role =
        roleService
            .findByCode("WORKER")
            .orElseThrow(() -> new IllegalStateException("WORKER role missing for demo marketer"));
    UUID departmentId =
        departmentRepository
            .findByTenantIdAndOrganizationIdAndDepartmentCode(tenantId, rootOrg.getId(), "SALES")
            .map(Department::getId)
            .orElse(null);

    UserDto created =
        userCreationService.createInternalUser(
            CreateInternalUserRequest.builder()
                .firstName(MARKETER_FIRST_NAME)
                .lastName(MARKETER_LAST_NAME)
                .contactValue(MARKETER_EMAIL)
                .contactType(ContactType.EMAIL)
                .organizationId(rootOrg.getId())
                .departmentId(departmentId)
                .roleId(role.getId())
                .jobTitleCode("SALES_REP")
                .invitationEmailSuppressed(true)
                .build());

    userRepository
        .findByTenantIdAndId(tenantId, created.getId())
        .ifPresent(
            user -> {
              user.setDemoSeed(true);
              userRepository.save(user);
            });
    return created.getId();
  }

  private UUID resolveDraftOwner(UUID tenantId, UUID fallbackUserId) {
    // Sandra Deal is the seeded sales-rep persona a playground visitor enters the sales views as.
    return userRepository
        .findFirstByTenantIdAndFirstNameAndLastNameAndIsActiveTrue(tenantId, "Sandra", "Deal")
        .map(User::getId)
        .orElse(fallbackUserId);
  }

  // ── Quote helpers ───────────────────────────────────────────────────────────

  private Quote createQuote(
      UUID customerId, UUID assignedToId, String quoteNumber, LocalDate validUntil, String notes) {
    QuoteCreateRequest req = new QuoteCreateRequest();
    req.setCustomerId(customerId);
    req.setAssignedToId(assignedToId);
    req.setModuleType(QUOTE_MODULE_TYPE);
    req.setQuoteNumber(quoteNumber);
    req.setCurrency(CURRENCY);
    req.setValidUntil(validUntil);
    req.setPaymentTerms("Net 30 days");
    req.setNotes(notes);
    return quoteService.createQuote(req);
  }

  private void addLotBackedLine(
      UUID quoteId, UUID productId, UUID qualityGradeId, UUID colourId, UUID lotId, String qty) {
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setQualityGradeId(qualityGradeId);
    req.setColorId(colourId);
    req.setSelectedLots(
        List.of(new QuoteLineLotSelectionRequest(lotId, null, new BigDecimal(qty))));
    req.setRequestedQty(new BigDecimal(qty));
    req.setUnit("M");
    req.setOfferedPrice(new BigDecimal(GABARDINE_OFFERED_PRICE));
    quoteService.addQuoteLineForDemoSeed(quoteId, req);
  }

  private void addFreeEntryLine(UUID quoteId, UUID productId, UUID qualityGradeId, UUID colourId) {
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setQualityGradeId(qualityGradeId);
    req.setColorId(colourId);
    req.setRequestedQty(new BigDecimal("400"));
    req.setUnit("M");
    req.setOfferedPrice(new BigDecimal(GABARDINE_LIST_PRICE));
    quoteService.addQuoteLineForDemoSeed(quoteId, req);
  }

  private String quoteNumber(String stem, UUID tenantId) {
    return stem + "-" + tenantSuffix(tenantId);
  }

  private String tenantSuffix(UUID tenantId) {
    return tenantId.toString().substring(0, 8).toUpperCase();
  }

  private record GradeTrio(QualityGrade first, QualityGrade second, QualityGrade waste) {}
}
