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
import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import com.fabricmanagement.production.execution.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.execution.batch.dto.ReserveRequest;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.masterdata.color.app.ColorService;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorCardSpec;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.CreateProductRequest;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import com.fabricmanagement.production.masterdata.qualitygrade.app.QualityGradeService;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the playground sales/ATP demo dataset (DEMO-SALES-1) for the "Pennine Mills Ltd" scenario:
 * quality grades, colour cards, products with GBP catalogue entries, dye lots with rolls/cartons, a
 * competing marketer quote whose soft lot-quantity intents shrink Navy free stock, one hard {@code
 * BatchReservation}, and a pre-filled draft quote — so the quote picker exercises every QLINE+ATP
 * behaviour (shade warning, remnant nudge, three-number stock, forced delivery status) on realistic
 * British data.
 *
 * <p>Runs post-clone inside the cloned tenant's context, writes via application services so real
 * invariants apply, and is idempotent (skips when the demo customer already exists).
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
   * Batch-header unit for fabric length lots. {@code chk_batch_unit} only admits KG / MT / PIECE
   * (see V001 + {@code BatchUnit}); metre granularity is carried by the rolls' {@code
   * StockUnit.lengthUnit = "M"}.
   */
  static final String BATCH_UNIT_METRES = "MT";

  private static final String CURRENCY = "GBP";
  private static final String QUOTE_MODULE_TYPE = "FABRIC";
  private static final String GRADING_REASON = "Initial grading (demo seed)";
  private static final String GABARDINE_LIST_PRICE = "6.80";
  private static final String GABARDINE_OFFERED_PRICE = "6.50";

  private final TradingPartnerService tradingPartnerService;
  private final ProductFacade productFacade;
  private final QualityGradeService qualityGradeService;
  private final ColorService colorService;
  private final BatchService batchService;
  private final StockUnitService stockUnitService;
  private final SalesProductService salesProductService;
  private final DiscountPolicyService discountPolicyService;
  private final ExchangeRateService exchangeRateService;
  private final QuoteService quoteService;
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

      Color navy = ensureColour("NAVY-01", "Navy", "#1F2A44");
      Color ecru = ensureColour("ECRU-02", "Ecru", "#F0EAD6");
      ensureColour("CHAR-03", "Charcoal", "#36454F");
      Color pfd =
          ensureColour(
              ColorCardSpec.builder()
                  .code("PFD-00")
                  .name("Prepared For Dyeing")
                  .colorType(ColorType.PFD)
                  .build());
      // Rust deliberately gets NO stock — the picker's passive-but-selectable colour case.
      ensureColour("RUST-04", "Rust", "#B7410E");

      ProductDto gabardine = productFacade.createProduct(product(ProductType.FABRIC, "M"));
      ProductDto combedYarn = productFacade.createProduct(product(ProductType.YARN, "KG"));
      ProductDto rawCotton = productFacade.createProduct(product(ProductType.FIBER, "KG"));

      catalogueEntry(gabardine, "PM-1453 Gabardine 155cm 190gsm", GABARDINE_LIST_PRICE);
      catalogueEntry(combedYarn, "PM-3001 Combed Yarn Ne 30/1", "4.20");
      catalogueEntry(rawCotton, "PM-9001 Raw Cotton", "1.85");

      ensureDiscountPolicy();
      // Reporting currency for demo tenants is USD (FinanceDemoSeeder); GBP quotes need this rate.
      exchangeRateService.saveRate(
          "USD", CURRENCY, new BigDecimal("0.79"), today, ExchangeRateSource.MANUAL);

      // ── Lots and pieces (per the DEMO-SALES-1 table) ──
      // Fabric lot headers use BATCH_UNIT_METRES ("MT"): production_execution_batch carries
      // chk_batch_unit CHECK (unit IN ('KG','MT','PIECE')) — "M" is rejected at INSERT. Metre
      // detail lives on the rolls (StockUnit.lengthUnit = "M"), which is also what the lot picker
      // surfaces for piece-backed length lots.
      BatchDto lot24011 =
          pendingBatch(
              gabardine,
              "LOT-24011",
              "2000",
              BATCH_UNIT_METRES,
              navy.getId(),
              "Main Navy dye lot, spring run");
      addRolls(lot24011.getId(), "24011", 16, "125", "36.800", fabricGrades.first().getId());
      releasePieceBackedBatch(lot24011.getId());

      BatchDto lot24012 =
          pendingBatch(
              gabardine,
              "LOT-24012",
              "3000",
              BATCH_UNIT_METRES,
              navy.getId(),
              "Second Navy dye lot — shade may vary");
      addRolls(lot24012.getId(), "24012", 24, "125", "36.800", fabricGrades.first().getId());
      releasePieceBackedBatch(lot24012.getId());

      BatchDto lot23087 =
          pendingBatch(
              gabardine,
              "LOT-23087",
              "290",
              BATCH_UNIT_METRES,
              navy.getId(),
              "Remnant lot — close it out");
      addRoll(lot23087.getId(), "23087", 1, "100", "29.500", fabricGrades.first().getId());
      addRoll(lot23087.getId(), "23087", 2, "95", "28.000", fabricGrades.first().getId());
      addRoll(lot23087.getId(), "23087", 3, "95", "28.000", fabricGrades.first().getId());
      releasePieceBackedBatch(lot23087.getId());

      BatchDto lot24020 =
          pendingBatch(
              gabardine,
              "LOT-24020",
              "450",
              BATCH_UNIT_METRES,
              ecru.getId(),
              "Second Quality Ecru — piece-backed demo lot");
      addRolls(lot24020.getId(), "24020", 18, "25", "25.000", fabricGrades.second().getId());
      releasePieceBackedBatch(lot24020.getId());

      BatchDto lot24031 =
          pendingBatch(
              combedYarn,
              "LOT-24031",
              "1200",
              "KG",
              pfd.getId(),
              "Combed yarn, prepared for dyeing");
      addCartons(lot24031.getId(), "24031", 48, "25.000", yarnGrades.first().getId());
      releasePieceBackedBatch(lot24031.getId());

      // Raw cotton skips the colour axis entirely — fibre cascade has no Colour step.
      pendingBatch(rawCotton, "LOT-24040", "5000", "KG", null, "Raw cotton, bulk store");

      // Waste-grade stock exists but must stay invisible to the picker (negative test).
      BatchDto lot23050 =
          pendingBatch(
              gabardine,
              "LOT-23050",
              "120",
              BATCH_UNIT_METRES,
              navy.getId(),
              "Waste grade — not saleable");
      addRoll(lot23050.getId(), "23050", 1, "60", "17.700", fabricGrades.waste().getId());
      addRoll(lot23050.getId(), "23050", 2, "60", "17.700", fabricGrades.waste().getId());
      releasePieceBackedBatch(lot23050.getId());

      // ── Actors ──
      TradingPartnerDto albion = createAlbionCustomer(tenantId);
      UUID emmaId = ensureMarketer(tenantId);
      UUID draftOwnerId = resolveDraftOwner(tenantId, emmaId);

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

  private Color ensureColour(String code, String name, String colorHex) {
    return colorService.list(true).stream()
        .filter(colour -> code.equalsIgnoreCase(colour.getCode()))
        .findFirst()
        .orElseGet(() -> colorService.create(code, name, colorHex));
  }

  private Color ensureColour(ColorCardSpec spec) {
    return colorService.list(true).stream()
        .filter(colour -> spec.code().equalsIgnoreCase(colour.getCode()))
        .findFirst()
        .orElseGet(() -> colorService.create(spec));
  }

  private CreateProductRequest product(ProductType productType, String unit) {
    return CreateProductRequest.builder().productType(productType).unit(unit).build();
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
            null));
  }

  private void ensureDiscountPolicy() {
    try {
      discountPolicyService.getActivePolicy(QUOTE_MODULE_TYPE);
    } catch (IllegalArgumentException missingPolicy) {
      DiscountPolicy policy = new DiscountPolicy();
      policy.setModuleType(QUOTE_MODULE_TYPE);
      policy.setBaseDiscountLimit(new BigDecimal("0.1000"));
      policy.setMinProfitMargin(new BigDecimal("0.0500"));
      policy.setRequireManagerAbove(new BigDecimal("0.1500"));
      discountPolicyService.savePolicy(policy);
    }
  }

  // ── Lot helpers ─────────────────────────────────────────────────────────────

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

  // ── Actor helpers ───────────────────────────────────────────────────────────

  private TradingPartnerDto createAlbionCustomer(UUID tenantId) {
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
    return tradingPartnerService.createPartner(req);
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
    quoteService.addQuoteLine(quoteId, req);
  }

  private void addFreeEntryLine(UUID quoteId, UUID productId, UUID qualityGradeId, UUID colourId) {
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setQualityGradeId(qualityGradeId);
    req.setColorId(colourId);
    req.setRequestedQty(new BigDecimal("400"));
    req.setUnit("M");
    req.setOfferedPrice(new BigDecimal(GABARDINE_LIST_PRICE));
    quoteService.addQuoteLine(quoteId, req);
  }

  private String quoteNumber(String stem, UUID tenantId) {
    return stem + "-" + tenantSuffix(tenantId);
  }

  private String tenantSuffix(UUID tenantId) {
    return tenantId.toString().substring(0, 8).toUpperCase();
  }

  private record GradeTrio(QualityGrade first, QualityGrade second, QualityGrade waste) {}
}
