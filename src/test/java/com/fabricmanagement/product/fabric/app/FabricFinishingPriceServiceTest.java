package com.fabricmanagement.product.fabric.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingPriceRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingPriceRepository;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingProcessRepository;
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

/** FABRIC-3c: which finishing price is in force for a process, a firm and a date. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FabricFinishingPriceServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID YORKSHIRE = UUID.randomUUID();
  private static final UUID CALDER = UUID.randomUUID();

  @Mock private FabricFinishingPriceRepository prices;
  @Mock private FabricFinishingProcessRepository processes;
  @Mock private TradingPartnerQueryService partners;

  private FabricFinishingPriceService service;
  private FabricFinishingProcess reactive;
  private FabricFinishingProcess vat;
  private final List<FabricFinishingPrice> stored = new ArrayList<>();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service = new FabricFinishingPriceService(prices, processes, partners);
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

  private FabricFinishingPrice add(
      FabricFinishingProcess process, UUID firm, String amount, String from) {
    return addInUnit(process, firm, process.getChargeUnit(), amount, from);
  }

  private FabricFinishingPrice addInUnit(
      FabricFinishingProcess process,
      UUID firm,
      FinishingChargeUnit unit,
      String amount,
      String from) {
    FabricFinishingPrice price =
        FabricFinishingPrice.record(
            TENANT,
            process.getId(),
            firm,
            unit,
            new BigDecimal(amount),
            "GBP",
            LocalDate.parse(from));
    ReflectionTestUtils.setField(price, "id", UUID.randomUUID());
    stored.add(price);
    return price;
  }

  private Optional<FabricFinishingPriceDto> inForce(UUID firm, String on) {
    return service.inForce(reactive.getId(), firm, LocalDate.parse(on));
  }

  private CreateFabricFinishingPriceRequest request(
      FabricFinishingProcess process, UUID firm, String amount, String from) {
    return new CreateFabricFinishingPriceRequest(
        process.getId(), firm, new BigDecimal(amount), "GBP", LocalDate.parse(from));
  }

  // ---------------------------------------------------------------- the price in force

  @Test
  void aFirmsOwnTariffBeatsTheGeneralPriceEvenWhenTheGeneralOneIsNewer() {
    FabricFinishingPrice tariff = add(reactive, YORKSHIRE, "1.60", "2026-01-01");
    add(reactive, null, "2.10", "2026-06-01");

    assertThat(inForce(YORKSHIRE, "2026-10-20"))
        .map(FabricFinishingPriceDto::id)
        .contains(tariff.getId());
  }

  @Test
  void aFirmWithoutATariffGetsTheGeneralPrice() {
    add(reactive, CALDER, "1.40", "2026-01-01");
    FabricFinishingPrice general = add(reactive, null, "2.10", "2026-01-01");

    assertThat(inForce(YORKSHIRE, "2026-10-20"))
        .map(FabricFinishingPriceDto::id)
        .contains(general.getId());
  }

  @Test
  void workAtAnOwnSiteGetsTheGeneralPriceAndNeverAFirmsTariff() {
    add(reactive, YORKSHIRE, "1.60", "2026-01-01");
    FabricFinishingPrice general = add(reactive, null, "2.10", "2026-01-01");

    assertThat(inForce(null, "2026-10-20"))
        .map(FabricFinishingPriceDto::id)
        .contains(general.getId());
  }

  @Test
  void theLatestPriceThatHasStartedWinsAndAFutureOneWaits() {
    add(reactive, null, "1.80", "2026-01-01");
    FabricFinishingPrice raised = add(reactive, null, "1.95", "2026-09-01");
    add(reactive, null, "2.20", "2026-12-01");

    assertThat(inForce(null, "2026-10-20"))
        .map(FabricFinishingPriceDto::id)
        .contains(raised.getId());
    assertThat(inForce(null, "2026-09-01"))
        .map(FabricFinishingPriceDto::id)
        .contains(raised.getId());
    assertThat(inForce(null, "2026-08-31"))
        .map(FabricFinishingPriceDto::unitPrice)
        .hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("1.80"));
  }

  @Test
  void nothingIsInForceBeforeTheFirstPriceOrForAnUnpricedProcess() {
    add(reactive, null, "1.80", "2026-06-01");

    assertThat(inForce(null, "2026-05-31")).isEmpty();
    assertThat(service.inForce(vat.getId(), null, LocalDate.parse("2026-10-20"))).isEmpty();
  }

  @Test
  void aPriceInTheProcessesOldUnitIsNotReadAsAPriceInItsNewUnit() {
    addInUnit(reactive, null, FinishingChargeUnit.KG, "4.00", "2026-01-01");

    assertThat(inForce(null, "2026-10-20")).isEmpty();
  }

  @Test
  void anUnknownProcessOrMissingDateHasNoPrice() {
    add(reactive, null, "1.80", "2026-01-01");

    assertThat(service.inForce(UUID.randomUUID(), null, LocalDate.parse("2026-10-20"))).isEmpty();
    assertThat(service.inForce(null, null, LocalDate.parse("2026-10-20"))).isEmpty();
    assertThat(service.inForce(reactive.getId(), null, null)).isEmpty();
  }

  @Test
  void theAnswerNamesTheProcessAndTheUnit() {
    add(reactive, null, "1.80", "2026-01-01");

    FabricFinishingPriceDto answer = inForce(null, "2026-10-20").orElseThrow();

    assertThat(answer.processCode()).isEqualTo("DYEING_REACTIVE");
    assertThat(answer.processName()).isEqualTo("Reactive dyeing");
    assertThat(answer.unit()).isEqualTo(FinishingChargeUnit.M);
    assertThat(answer.currency()).isEqualTo("GBP");
    assertThat(answer.partnerId()).isNull();
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
            UUID.randomUUID(), null, BigDecimal.ONE, "GBP", LocalDate.parse("2026-10-01"));
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
        .map(FabricFinishingPriceDto::id)
        .contains(earlier.getId());
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
