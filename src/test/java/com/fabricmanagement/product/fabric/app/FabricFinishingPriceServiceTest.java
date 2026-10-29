package com.fabricmanagement.product.fabric.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPriceClass;
import com.fabricmanagement.product.fabric.domain.pricing.PriceContext;
import com.fabricmanagement.product.fabric.domain.pricing.PriceScope;
import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.domain.vocabulary.GsmBasis;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingPriceRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.fabric.dto.FinishingPriceQuery;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingPriceClassRepository;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingPriceRepository;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingProcessRepository;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FABRIC-3c: the finishing price list. Which prices are in force for a process, a firm, a date and
 * a lot is decided by {@code FinishingPriceResolver}; this covers what the service adds on top:
 * validating and recording prices, and naming grades and classes in the answer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FabricFinishingPriceServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID YORKSHIRE = UUID.randomUUID();
  private static final UUID CALDER = UUID.randomUUID();

  @Mock private FabricFinishingPriceRepository prices;
  @Mock private FabricFinishingProcessRepository processes;
  @Mock private FabricFinishingPriceClassRepository priceClasses;
  @Mock private TradingPartnerQueryService partners;
  @Mock private QualityGradeQueryService qualityGrades;

  private FabricFinishingPriceService service;
  private FabricFinishingProcess reactive;
  private FabricFinishingProcess vat;
  private FabricFinishingPriceClass dark;
  private FabricFinishingPriceClass black;
  private QualityGradeReference first;
  private QualityGradeReference second;
  private final List<FabricFinishingPrice> stored = new ArrayList<>();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service =
        new FabricFinishingPriceService(prices, processes, priceClasses, partners, qualityGrades);
    reactive = process("DYEING_REACTIVE", "Reactive dyeing", 1, FinishingChargeUnit.M);
    vat = process("DYEING_VAT", "Vat dyeing", 2, FinishingChargeUnit.KG);
    when(processes.findByTenantIdAndDeletedAtIsNull(TENANT)).thenReturn(List.of(reactive, vat));
    when(processes.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, reactive.getId()))
        .thenReturn(Optional.of(reactive));
    when(processes.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, vat.getId()))
        .thenReturn(Optional.of(vat));
    when(prices.findByTenantIdAndDeletedAtIsNull(TENANT)).thenReturn(stored);
    when(prices.findByTenantIdAndProcessIdAndDeletedAtIsNull(any(), any()))
        .thenAnswer(
            call ->
                stored.stream()
                    .filter(price -> price.getProcessId().equals(call.getArgument(1)))
                    .toList());
    when(prices.saveAndFlush(any(FabricFinishingPrice.class)))
        .thenAnswer(
            call -> {
              FabricFinishingPrice saved = call.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
              stored.add(saved);
              return saved;
            });
    dark = priceClass("DARK", "Dark", 3);
    black = priceClass("BLACK", "Black", 5);
    when(priceClasses.findByTenantIdAndDeletedAtIsNull(TENANT)).thenReturn(List.of(dark, black));
    when(priceClasses.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, dark.getId()))
        .thenReturn(Optional.of(dark));
    when(priceClasses.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, black.getId()))
        .thenReturn(Optional.of(black));
    first = grade("1A", "First quality", 1);
    second = grade("B", "Second quality", 3);
    when(qualityGrades.findSalesGradeReferences(ProductType.FABRIC, true))
        .thenReturn(List.of(first, second));
    when(qualityGrades.findReferencesByIds(anyCollection()))
        .thenAnswer(
            call -> {
              java.util.Collection<UUID> wanted = call.getArgument(0);
              return List.of(first, second).stream()
                  .filter(grade -> wanted.contains(grade.id()))
                  .toList();
            });
    when(partners.findClassification(TENANT, YORKSHIRE))
        .thenReturn(
            Optional.of(
                new TradingPartnerQueryService.PartnerClassification(
                    PartnerType.SUBCONTRACTOR, PartnerStatus.ACTIVE, true)));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static FabricFinishingProcess process(
      String code, String name, int order, FinishingChargeUnit unit) {
    FabricFinishingProcess process =
        FabricFinishingProcess.defineTenant(
            TENANT, code, name, null, order, FabricFinishingCategory.DYEING, unit);
    ReflectionTestUtils.setField(process, "id", UUID.randomUUID());
    return process;
  }

  private static FabricFinishingPriceClass priceClass(String code, String name, int order) {
    FabricFinishingPriceClass priceClass =
        FabricFinishingPriceClass.defineTenant(TENANT, code, name, null, order);
    ReflectionTestUtils.setField(priceClass, "id", UUID.randomUUID());
    return priceClass;
  }

  private static QualityGradeReference grade(String code, String name, int rank) {
    return new QualityGradeReference(
        UUID.randomUUID(), code, name, rank, BigDecimal.ONE, true, true);
  }

  private FabricFinishingPrice add(
      FabricFinishingProcess process, UUID firm, String amount, String from) {
    return addScoped(process, PriceScope.plain(firm), amount, from);
  }

  private FabricFinishingPrice addScoped(
      FabricFinishingProcess process, PriceScope scope, String amount, String from) {
    return addInUnit(process, scope, process.getChargeUnit(), amount, from);
  }

  private FabricFinishingPrice addInUnit(
      FabricFinishingProcess process,
      PriceScope scope,
      FinishingChargeUnit unit,
      String amount,
      String from) {
    FabricFinishingPrice price =
        FabricFinishingPrice.record(
            TENANT,
            process.getId(),
            scope,
            unit,
            new BigDecimal(amount),
            "GBP",
            LocalDate.parse(from));
    ReflectionTestUtils.setField(price, "id", UUID.randomUUID());
    stored.add(price);
    return price;
  }

  private static PriceScope graded(UUID firm, QualityGradeReference grade) {
    return new PriceScope(firm, null, grade.id(), null, null, null, null, null);
  }

  private List<FabricFinishingPriceDto> inForce(UUID firm, String on) {
    return service.inForce(query(firm, on, PriceContext.unknown()));
  }

  private List<FabricFinishingPriceDto> inForce(UUID firm, String on, PriceContext context) {
    return service.inForce(query(firm, on, context));
  }

  private FinishingPriceQuery query(UUID firm, String on, PriceContext context) {
    return new FinishingPriceQuery(
        reactive.getId(),
        firm,
        LocalDate.parse(on),
        context.priceClassId(),
        context.formation(),
        context.gsmGreige(),
        context.gsmFinished());
  }

  private CreateFabricFinishingPriceRequest request(
      FabricFinishingProcess process, UUID firm, String amount, String from) {
    return new CreateFabricFinishingPriceRequest(
        process.getId(),
        firm,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        new BigDecimal(amount),
        "GBP",
        LocalDate.parse(from));
  }

  private CreateFabricFinishingPriceRequest narrowed(
      UUID grade,
      UUID priceClass,
      FabricFormation formation,
      String gsmFrom,
      String gsmTo,
      GsmBasis basis,
      String from) {
    return new CreateFabricFinishingPriceRequest(
        reactive.getId(),
        YORKSHIRE,
        FinishingBillingBasis.SENT,
        grade,
        priceClass,
        formation,
        gsmFrom == null ? null : new BigDecimal(gsmFrom),
        gsmTo == null ? null : new BigDecimal(gsmTo),
        basis,
        new BigDecimal("2.40"),
        "GBP",
        LocalDate.parse(from));
  }

  // ---------------------------------------------------------------- the prices in force

  @Test
  void aFirmsOwnTariffBeatsTheGeneralPriceEvenWhenTheGeneralOneIsNewer() {
    FabricFinishingPrice tariff = add(reactive, YORKSHIRE, "1.60", "2026-01-01");
    add(reactive, null, "2.10", "2026-06-01");

    assertThat(inForce(YORKSHIRE, "2026-10-20"))
        .extracting(FabricFinishingPriceDto::id)
        .containsExactly(tariff.getId());
  }

  @Test
  void workAtAnOwnSiteGetsTheGeneralPriceAndNeverAFirmsTariff() {
    add(reactive, YORKSHIRE, "1.60", "2026-01-01");
    FabricFinishingPrice general = add(reactive, null, "2.10", "2026-01-01");

    assertThat(inForce(null, "2026-10-20"))
        .extracting(FabricFinishingPriceDto::id)
        .containsExactly(general.getId());
  }

  @Test
  void theLatestPriceThatHasStartedWinsAndAFutureOneWaits() {
    add(reactive, null, "1.80", "2026-01-01");
    FabricFinishingPrice raised = add(reactive, null, "1.95", "2026-09-01");
    add(reactive, null, "2.20", "2026-12-01");

    assertThat(inForce(null, "2026-10-20"))
        .extracting(FabricFinishingPriceDto::id)
        .containsExactly(raised.getId());
    assertThat(inForce(null, "2026-08-31"))
        .extracting(FabricFinishingPriceDto::unitPrice)
        .singleElement()
        .satisfies(amount -> assertThat(amount).isEqualByComparingTo("1.80"));
  }

  @Test
  void nothingIsInForceForAnUnpricedProcessBeforeTheFirstPriceOrForAnUnknownProcess() {
    add(reactive, null, "1.80", "2026-06-01");

    assertThat(inForce(null, "2026-05-31")).isEmpty();
    assertThat(
            service.inForce(
                new FinishingPriceQuery(
                    vat.getId(), null, LocalDate.parse("2026-10-20"), null, null, null, null)))
        .isEmpty();
    assertThat(
            service.inForce(
                new FinishingPriceQuery(
                    UUID.randomUUID(),
                    null,
                    LocalDate.parse("2026-10-20"),
                    null,
                    null,
                    null,
                    null)))
        .isEmpty();
  }

  @Test
  void aQueryWithoutAProcessOrADateHasNoPrices() {
    add(reactive, null, "1.80", "2026-01-01");

    assertThat(service.inForce(null)).isEmpty();
    assertThat(
            service.inForce(
                new FinishingPriceQuery(
                    null, null, LocalDate.parse("2026-10-20"), null, null, null, null)))
        .isEmpty();
    assertThat(
            service.inForce(
                new FinishingPriceQuery(reactive.getId(), null, null, null, null, null, null)))
        .isEmpty();
  }

  @Test
  void aPriceInTheProcessesOldUnitIsNotReadAsAPriceInItsNewUnit() {
    addInUnit(reactive, PriceScope.plain(null), FinishingChargeUnit.KG, "4.00", "2026-01-01");

    assertThat(inForce(null, "2026-10-20")).isEmpty();
  }

  @Test
  void theAnswerNamesTheProcessTheUnitTheBasisAndTheNarrowing() {
    addScoped(
        reactive,
        new PriceScope(
            YORKSHIRE,
            FinishingBillingBasis.SENT,
            null,
            dark.getId(),
            FabricFormation.KNITTED,
            new BigDecimal("150"),
            null,
            GsmBasis.GREIGE),
        "1.80",
        "2026-01-01");

    FabricFinishingPriceDto answer =
        inForce(
                YORKSHIRE,
                "2026-10-20",
                new PriceContext(
                    dark.getId(), FabricFormation.KNITTED, new BigDecimal("180"), null))
            .get(0);

    assertThat(answer.processCode()).isEqualTo("DYEING_REACTIVE");
    assertThat(answer.processName()).isEqualTo("Reactive dyeing");
    assertThat(answer.unit()).isEqualTo(FinishingChargeUnit.M);
    assertThat(answer.currency()).isEqualTo("GBP");
    assertThat(answer.partnerId()).isEqualTo(YORKSHIRE);
    assertThat(answer.billingBasis()).isEqualTo(FinishingBillingBasis.SENT);
    assertThat(answer.priceClassId()).isEqualTo(dark.getId());
    assertThat(answer.priceClassCode()).isEqualTo("DARK");
    assertThat(answer.priceClassName()).isEqualTo("Dark");
    assertThat(answer.formation()).isEqualTo(FabricFormation.KNITTED);
    assertThat(answer.gsmFrom()).isEqualByComparingTo("150");
    assertThat(answer.gsmTo()).isNull();
    assertThat(answer.gsmBasis()).isEqualTo(GsmBasis.GREIGE);
    assertThat(answer.qualityGradeId()).isNull();
  }

  @Test
  void aFirmThatPricesByGradeAnswersOnePricePerGradeUngradedFirstThenByRank() {
    FabricFinishingPrice secondPrice =
        addScoped(reactive, graded(YORKSHIRE, second), "0.00", "2026-01-01");
    FabricFinishingPrice firstPrice =
        addScoped(reactive, graded(YORKSHIRE, first), "2.10", "2026-01-01");
    FabricFinishingPrice everyGrade = add(reactive, YORKSHIRE, "2.00", "2026-01-01");

    List<FabricFinishingPriceDto> answer = inForce(YORKSHIRE, "2026-10-20");

    assertThat(answer)
        .extracting(FabricFinishingPriceDto::id)
        .containsExactly(everyGrade.getId(), firstPrice.getId(), secondPrice.getId());
    assertThat(answer.get(1).qualityGradeCode()).isEqualTo("1A");
    assertThat(answer.get(1).qualityGradeName()).isEqualTo("First quality");
    assertThat(answer.get(2).qualityGradeCode()).isEqualTo("B");
    assertThat(answer.get(2).unitPrice()).isEqualByComparingTo("0");
    assertThat(answer.get(0).qualityGradeCode()).isNull();
  }

  // ---------------------------------------------------------------- adding a price

  @Test
  void aNewPriceTakesTheProcessesChargeUnit() {
    FabricFinishingPriceDto created = service.create(request(vat, YORKSHIRE, "3.25", "2026-10-01"));

    assertThat(created.unit()).isEqualTo(FinishingChargeUnit.KG);
    assertThat(created.partnerId()).isEqualTo(YORKSHIRE);
    assertThat(created.processCode()).isEqualTo("DYEING_VAT");
    assertThat(stored).hasSize(1);
  }

  @Test
  void aProcessCanBePricedAtSeveralDatesAndForTheGeneralAndTheFirmAlike() {
    service.create(request(reactive, null, "2.10", "2026-10-01"));
    service.create(request(reactive, null, "2.30", "2026-11-01"));
    service.create(request(reactive, YORKSHIRE, "1.90", "2026-10-01"));

    assertThat(stored).hasSize(3);
  }

  @Test
  void theSameFirmAndDateCannotBePricedTwice() {
    service.create(request(reactive, YORKSHIRE, "1.90", "2026-10-01"));

    assertThatThrownBy(() -> service.create(request(reactive, YORKSHIRE, "2.00", "2026-10-01")))
        .isInstanceOfSatisfying(
            DomainException.class,
            problem -> {
              assertThat(problem.getErrorCode()).isEqualTo("FINISHING_PRICE_DUPLICATE");
              assertThat(problem.getHttpStatus()).isEqualTo(409);
            });
    assertThat(stored).hasSize(1);
  }

  @Test
  void twoGeneralPricesCannotShareADateEither() {
    service.create(request(reactive, null, "2.10", "2026-10-01"));

    assertThatThrownBy(() -> service.create(request(reactive, null, "2.40", "2026-10-01")))
        .isInstanceOfSatisfying(
            DomainException.class,
            problem -> assertThat(problem.getErrorCode()).isEqualTo("FINISHING_PRICE_DUPLICATE"));
  }

  @Test
  void aRaceThatTheDatabaseCatchesIsReportedAsTheSameDuplicate() {
    doThrow(new DataIntegrityViolationException("uq_fabric_finishing_price_live"))
        .when(prices)
        .saveAndFlush(any(FabricFinishingPrice.class));

    assertThatThrownBy(() -> service.create(request(reactive, null, "2.10", "2026-10-01")))
        .isInstanceOfSatisfying(
            DomainException.class,
            problem -> assertThat(problem.getErrorCode()).isEqualTo("FINISHING_PRICE_DUPLICATE"));
  }

  @Test
  void aRetiredProcessCannotBePriced() {
    reactive.update("Reactive dyeing", null, 1, FinishingChargeUnit.M, false);

    assertThatThrownBy(() -> service.create(request(reactive, null, "2.10", "2026-10-01")))
        .isInstanceOfSatisfying(
            DomainException.class,
            problem -> {
              assertThat(problem.getErrorCode()).isEqualTo("FINISHING_PRICE_PROCESS_RETIRED");
              assertThat(problem.getHttpStatus()).isEqualTo(422);
            });
  }

  @Test
  void anUnknownProcessOrAnInactiveFirmIsNotFound() {
    CreateFabricFinishingPriceRequest unknownProcess =
        new CreateFabricFinishingPriceRequest(
            UUID.randomUUID(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            BigDecimal.ONE,
            "GBP",
            LocalDate.parse("2026-10-01"));
    assertThatThrownBy(() -> service.create(unknownProcess)).isInstanceOf(NotFoundException.class);

    when(partners.findClassification(TENANT, CALDER))
        .thenReturn(
            Optional.of(
                new TradingPartnerQueryService.PartnerClassification(
                    PartnerType.SUBCONTRACTOR, PartnerStatus.SUSPENDED, true)));
    assertThatThrownBy(() -> service.create(request(reactive, CALDER, "1.40", "2026-10-01")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> service.create(request(reactive, UUID.randomUUID(), "1.40", "2026-10-01")))
        .isInstanceOf(NotFoundException.class);
    assertThat(stored).isEmpty();
  }

  @Test
  void aNarrowedPriceRecordsItsGradeClassFormationWeightAndBasis() {
    FabricFinishingPriceDto created =
        service.create(
            narrowed(
                first.id(),
                black.getId(),
                FabricFormation.WOVEN,
                "150",
                "200",
                GsmBasis.FINISHED,
                "2026-10-01"));

    assertThat(created.billingBasis()).isEqualTo(FinishingBillingBasis.SENT);
    assertThat(created.qualityGradeId()).isEqualTo(first.id());
    assertThat(created.qualityGradeCode()).isEqualTo("1A");
    assertThat(created.priceClassCode()).isEqualTo("BLACK");
    assertThat(created.formation()).isEqualTo(FabricFormation.WOVEN);
    assertThat(created.gsmFrom()).isEqualByComparingTo("150");
    assertThat(created.gsmTo()).isEqualByComparingTo("200");
    assertThat(created.gsmBasis()).isEqualTo(GsmBasis.FINISHED);
  }

  @Test
  void aPriceDefaultsToWhatCameBack() {
    assertThat(service.create(request(reactive, null, "2.10", "2026-10-01")).billingBasis())
        .isEqualTo(FinishingBillingBasis.RETURNED);
  }

  @Test
  void theSameDateCanCarryDifferentNarrowingsButNotTheSameOne() {
    service.create(narrowed(first.id(), dark.getId(), null, null, null, null, "2026-10-01"));
    service.create(narrowed(second.id(), dark.getId(), null, null, null, null, "2026-10-01"));
    service.create(narrowed(first.id(), black.getId(), null, null, null, null, "2026-10-01"));
    service.create(
        narrowed(first.id(), dark.getId(), null, "100", "150", GsmBasis.GREIGE, "2026-10-01"));
    service.create(
        narrowed(first.id(), dark.getId(), null, "100", "150", GsmBasis.FINISHED, "2026-10-01"));

    assertThat(stored).hasSize(5);
    assertThatThrownBy(
            () ->
                service.create(
                    narrowed(
                        first.id(),
                        dark.getId(),
                        null,
                        "100.0",
                        "150",
                        GsmBasis.GREIGE,
                        "2026-10-01")))
        .isInstanceOfSatisfying(
            DomainException.class,
            problem -> assertThat(problem.getErrorCode()).isEqualTo("FINISHING_PRICE_DUPLICATE"));
  }

  @Test
  void aGradeThatIsNotAnActiveFabricGradeIsNotFound() {
    assertThatThrownBy(
            () ->
                service.create(
                    narrowed(UUID.randomUUID(), null, null, null, null, null, "2026-10-01")))
        .isInstanceOf(NotFoundException.class);
    assertThat(stored).isEmpty();
  }

  @Test
  void aPriceClassThatDoesNotExistIsNotFoundAndARetiredOneIsRefused() {
    assertThatThrownBy(
            () ->
                service.create(
                    narrowed(null, UUID.randomUUID(), null, null, null, null, "2026-10-01")))
        .isInstanceOf(NotFoundException.class);

    dark.update("Dark", null, 3, false);
    assertThatThrownBy(
            () ->
                service.create(narrowed(null, dark.getId(), null, null, null, null, "2026-10-01")))
        .isInstanceOfSatisfying(
            DomainException.class,
            problem -> {
              assertThat(problem.getErrorCode()).isEqualTo("FINISHING_PRICE_CLASS_RETIRED");
              assertThat(problem.getHttpStatus()).isEqualTo(422);
            });
    assertThat(stored).isEmpty();
  }

  @Test
  void aWeightRangeThatMakesNoSenseIsRefused() {
    assertThatThrownBy(
            () ->
                service.create(
                    narrowed(null, null, null, "200", "150", GsmBasis.GREIGE, "2026-10-01")))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () -> service.create(narrowed(null, null, null, "150", "200", null, "2026-10-01")))
        .isInstanceOf(DomainException.class);
    assertThat(stored).isEmpty();
  }

  // ---------------------------------------------------------------- withdrawing a price

  @Test
  void aWithdrawnPriceIsSoftDeletedSoAnEarlierOneAppliesAgain() {
    FabricFinishingPrice earlier = add(reactive, null, "1.80", "2026-01-01");
    FabricFinishingPrice mistake = add(reactive, null, "18.00", "2026-10-01");
    when(prices.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, mistake.getId()))
        .thenReturn(Optional.of(mistake));

    service.remove(mistake.getId());

    assertThat(mistake.getDeletedAt()).isNotNull();
    stored.remove(mistake);
    assertThat(inForce(null, "2026-10-20"))
        .extracting(FabricFinishingPriceDto::id)
        .containsExactly(earlier.getId());
  }

  @Test
  void withdrawingAnUnknownPriceIsNotFound() {
    UUID unknown = UUID.randomUUID();
    when(prices.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, unknown))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.remove(unknown)).isInstanceOf(NotFoundException.class);
  }

  // ---------------------------------------------------------------- the list

  @Test
  void theListFollowsTheDictionaryTheGeneralPriceFirstAndTheLatestDateFirst() {
    FabricFinishingPrice vatPrice = add(vat, null, "3.00", "2026-01-01");
    FabricFinishingPrice firm = add(reactive, YORKSHIRE, "1.60", "2026-03-01");
    FabricFinishingPrice generalOld = add(reactive, null, "1.80", "2026-01-01");
    FabricFinishingPrice generalNew = add(reactive, null, "1.95", "2026-09-01");

    assertThat(service.list(null, null))
        .extracting(FabricFinishingPriceDto::id)
        .containsExactly(generalNew.getId(), generalOld.getId(), firm.getId(), vatPrice.getId());
  }

  @Test
  void theListOrdersGradesByRankAndNamesTheGradeAndClass() {
    FabricFinishingPrice secondPrice =
        addScoped(
            reactive,
            new PriceScope(YORKSHIRE, null, second.id(), black.getId(), null, null, null, null),
            "0.00",
            "2026-01-01");
    FabricFinishingPrice firstPrice =
        addScoped(
            reactive,
            new PriceScope(YORKSHIRE, null, first.id(), dark.getId(), null, null, null, null),
            "2.10",
            "2026-01-01");
    FabricFinishingPrice everyGrade = add(reactive, YORKSHIRE, "2.00", "2026-01-01");

    List<FabricFinishingPriceDto> listed = service.list(reactive.getId(), YORKSHIRE);

    assertThat(listed)
        .extracting(FabricFinishingPriceDto::id)
        .containsExactly(everyGrade.getId(), firstPrice.getId(), secondPrice.getId());
    assertThat(listed.get(1).qualityGradeName()).isEqualTo("First quality");
    assertThat(listed.get(1).priceClassName()).isEqualTo("Dark");
    assertThat(listed.get(2).priceClassCode()).isEqualTo("BLACK");
  }

  @Test
  void theListCanBeNarrowedToOneProcessOrOneFirm() {
    add(vat, null, "3.00", "2026-01-01");
    FabricFinishingPrice firm = add(reactive, YORKSHIRE, "1.60", "2026-03-01");
    add(reactive, null, "1.80", "2026-01-01");

    assertThat(service.list(reactive.getId(), YORKSHIRE))
        .extracting(FabricFinishingPriceDto::id)
        .containsExactly(firm.getId());
    assertThat(service.list(vat.getId(), null)).hasSize(1);
    assertThat(service.list(null, CALDER)).isEmpty();
  }
}
