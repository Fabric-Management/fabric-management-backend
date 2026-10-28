package com.fabricmanagement.production.core.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.product.color.api.query.ColorQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fabric.api.facade.FabricFacade;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.dto.FabricFinishingProcessDto;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStep;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStepStatus;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.domain.event.BatchFinishingReceivedEvent;
import com.fabricmanagement.production.core.batch.domain.event.BatchFinishingSentEvent;
import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.core.batch.dto.BatchFinishingDtos;
import com.fabricmanagement.production.core.batch.infra.repository.BatchFinishingStepRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchFinishingServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final UUID PARTNER = UUID.randomUUID();
  private static final UUID NAVY = UUID.randomUUID();
  private static final UUID RED = UUID.randomUUID();
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

  private static final FabricFinishingProcessDto DYEING =
      process("DYEING_REACTIVE", "Reactive dyeing", FabricFinishingCategory.DYEING);
  private static final FabricFinishingProcessDto MERCERISING =
      process("MERCERISING", "Mercerising", FabricFinishingCategory.PRETREATMENT);

  @Mock private BatchRepository batchRepository;
  @Mock private BatchFinishingStepRepository stepRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private FabricFacade fabrics;
  @Mock private ColorQueryService colours;
  @Mock private TradingPartnerQueryService partners;
  @Mock private ApplicationEventPublisher events;

  private BatchFinishingService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(UUID.randomUUID());
    service =
        new BatchFinishingService(
            batchRepository,
            stepRepository,
            stockUnitRepository,
            fabrics,
            colours,
            partners,
            events,
            Clock.fixed(Instant.parse("2026-10-20T09:00:00Z"), ZoneOffset.UTC));

    when(fabrics.findUsableFinishingProcess(DYEING.id())).thenReturn(Optional.of(DYEING));
    when(fabrics.findUsableFinishingProcess(MERCERISING.id())).thenReturn(Optional.of(MERCERISING));
    when(fabrics.findFinishingProcesses(anyCollection()))
        .thenReturn(Map.of(DYEING.id(), DYEING, MERCERISING.id(), MERCERISING));
    when(colours.findActiveReferenceById(NAVY)).thenReturn(Optional.of(colour(NAVY)));
    when(colours.findActiveReferenceById(RED)).thenReturn(Optional.of(colour(RED)));
    when(partners.findClassification(TENANT, PARTNER))
        .thenReturn(
            Optional.of(
                new TradingPartnerQueryService.PartnerClassification(
                    PartnerType.SUBCONTRACTOR, PartnerStatus.ACTIVE, true)));
    when(stepRepository.save(any(BatchFinishingStep.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(batchRepository.save(any(Batch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  // ── send ───────────────────────────────────────────────────────────────────

  @Test
  void sendingTakesTheWholeLotOutAndHoldsIt() {
    Batch lot = lot(BatchStatus.AVAILABLE, "M", "3000");

    BatchFinishingDtos.StepDto dto = service.send(BATCH_ID, send(DYEING, PARTNER, "DH-4471", NAVY));

    assertThat(dto.status()).isEqualTo(BatchFinishingStepStatus.SENT);
    assertThat(dto.sentMetres()).isEqualByComparingTo("3000");
    assertThat(dto.sentOn()).isEqualTo(TODAY);
    assertThat(dto.sequence()).isEqualTo(1);
    assertThat(dto.firmJobNumber()).isEqualTo("DH-4471");
    assertThat(dto.partnerId()).isEqualTo(PARTNER);
    assertThat(dto.colorId()).isEqualTo(NAVY);
    assertThat(dto.category()).isEqualTo(FabricFinishingCategory.DYEING);
    assertThat(lot.getStatus()).isEqualTo(BatchStatus.ON_HOLD);
    assertThat(lot.getColorId()).as("the colour is given only when the lot returns").isNull();
    assertThat(lot.getQuantity()).isEqualByComparingTo("3000");

    ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
    verify(events).publishEvent(published.capture());
    assertThat(published.getValue()).isInstanceOf(BatchFinishingSentEvent.class);
  }

  @Test
  void aStepAfterAnotherGetsTheNextSequence() {
    lot(BatchStatus.AVAILABLE, "M", "100");
    when(stepRepository.lastSequence(TENANT, BATCH_ID)).thenReturn(2);

    BatchFinishingDtos.StepDto dto = service.send(BATCH_ID, send(MERCERISING, null, null, null));

    assertThat(dto.sequence()).isEqualTo(3);
    assertThat(dto.partnerId()).as("no partner means an own site").isNull();
  }

  @Test
  void dyeingNeedsTheColourItGives() {
    lot(BatchStatus.AVAILABLE, "M", "100");

    assertThatThrownBy(() -> service.send(BATCH_ID, send(DYEING, PARTNER, null, null)))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("needs the colour");
    verify(stepRepository, never()).save(any(BatchFinishingStep.class));
  }

  @Test
  void aProcessWithoutColourRefusesOne() {
    lot(BatchStatus.AVAILABLE, "M", "100");

    assertThatThrownBy(() -> service.send(BATCH_ID, send(MERCERISING, PARTNER, null, NAVY)))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("does not give a colour");
  }

  @Test
  void aColourThatIsNotActiveIsNotFound() {
    lot(BatchStatus.AVAILABLE, "M", "100");
    UUID unknown = UUID.randomUUID();
    when(colours.findActiveReferenceById(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.send(BATCH_ID, send(DYEING, PARTNER, null, unknown)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void aPartnerThatIsNotActiveIsNotFound() {
    lot(BatchStatus.AVAILABLE, "M", "100");
    UUID inactive = UUID.randomUUID();
    when(partners.findClassification(TENANT, inactive))
        .thenReturn(
            Optional.of(
                new TradingPartnerQueryService.PartnerClassification(
                    PartnerType.SUBCONTRACTOR, PartnerStatus.ACTIVE, false)));

    assertThatThrownBy(() -> service.send(BATCH_ID, send(MERCERISING, inactive, null, null)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void onlyAnAvailableLotGoesOut() {
    lot(BatchStatus.PENDING_QC, "M", "100");

    assertThatThrownBy(() -> service.send(BATCH_ID, send(MERCERISING, null, null, null)))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("only an available lot can go out");
  }

  @Test
  void aLotWithReservedMetresCannotGoOutWhole() {
    Batch lot = lot(BatchStatus.AVAILABLE, "M", "100");
    lot.setReservedQuantity(new BigDecimal("10"));

    assertThatThrownBy(() -> service.send(BATCH_ID, send(MERCERISING, null, null, null)))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("reserved or consumed");
  }

  @Test
  void aLotWithReservedRollsCannotGoOutWhole() {
    lot(BatchStatus.AVAILABLE, "M", "100");
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrueAndStatusIn(
            any(UUID.class), any(UUID.class), anyCollection()))
        .thenReturn(2L);

    assertThatThrownBy(() -> service.send(BATCH_ID, send(MERCERISING, null, null, null)))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("rolls");
    verify(stockUnitRepository)
        .countByTenantIdAndBatchIdAndIsActiveTrueAndStatusIn(
            TENANT,
            BATCH_ID,
            java.util.Set.of(StockUnitStatus.RESERVED, StockUnitStatus.IN_TRANSIT));
  }

  @Test
  void aLotCountedInKilogramsCannotBeSentByTheMetre() {
    lot(BatchStatus.AVAILABLE, "KG", "100");

    assertThatThrownBy(() -> service.send(BATCH_ID, send(MERCERISING, null, null, null)))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("counts metres");
  }

  @Test
  void onlyFabricLotsHaveFinishingSteps() {
    Batch lot = lot(BatchStatus.AVAILABLE, "M", "100");
    lot.setProductType(ProductType.YARN);

    assertThatThrownBy(() -> service.send(BATCH_ID, send(MERCERISING, null, null, null)))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("fabric lots only");
  }

  // ── receive ────────────────────────────────────────────────────────────────

  @Test
  void receivingReleasesTheLotGivesTheColourAndLeavesTheQuantityAlone() {
    Batch lot = lot(BatchStatus.ON_HOLD, "M", "3000");
    BatchFinishingStep step = outStep(1, DYEING, NAVY);
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, step.getId()))
        .thenReturn(Optional.of(step));
    when(stepRepository.findByTenantIdAndBatchIdAndIsActiveTrueOrderBySequenceAsc(TENANT, BATCH_ID))
        .thenReturn(List.of(step));

    BatchFinishingDtos.StepDto dto =
        service.receive(
            step.getId(),
            new BatchFinishingDtos.ReceiveRequest(
                new BigDecimal("2940"), null, null, new BigDecimal("2100.00"), "GBP", null));

    assertThat(dto.status()).isEqualTo(BatchFinishingStepStatus.DONE);
    assertThat(dto.returnedMetres()).isEqualByComparingTo("2940");
    assertThat(dto.returnedOn()).isEqualTo(TODAY);
    assertThat(dto.lossMetres()).isEqualByComparingTo("60");
    assertThat(lot.getStatus()).isEqualTo(BatchStatus.AVAILABLE);
    assertThat(lot.getColorId()).isEqualTo(NAVY);
    assertThat(lot.getQuantity())
        .as("the step records the returned metres; the lot's pieces are re-measured separately")
        .isEqualByComparingTo("3000");

    ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
    verify(events).publishEvent(published.capture());
    BatchFinishingReceivedEvent event = (BatchFinishingReceivedEvent) published.getValue();
    assertThat(event.getReturnedMetres()).isEqualByComparingTo("2940");
    assertThat(event.getCostCurrency()).isEqualTo("GBP");
    assertThat(event.getColourId()).isEqualTo(NAVY);
  }

  @Test
  void anOverDyeReplacesTheColourAndTheEarlierOneStaysOnItsStep() {
    Batch lot = lot(BatchStatus.ON_HOLD, "M", "500");
    lot.assignColor(NAVY);
    BatchFinishingStep first = outStep(1, DYEING, NAVY);
    first.receive(new BigDecimal("495"), LocalDate.of(2026, 9, 1), null, null, null, null);
    BatchFinishingStep second = outStep(2, DYEING, RED);
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, second.getId()))
        .thenReturn(Optional.of(second));
    when(stepRepository.findByTenantIdAndBatchIdAndIsActiveTrueOrderBySequenceAsc(TENANT, BATCH_ID))
        .thenReturn(List.of(first, second));

    service.receive(
        second.getId(),
        new BatchFinishingDtos.ReceiveRequest(new BigDecimal("490"), null, null, null, null, null));

    assertThat(lot.getColorId()).isEqualTo(RED);
    assertThat(first.getColourId()).isEqualTo(NAVY);
  }

  @Test
  void aStepWithoutColourLeavesTheLotsColourAlone() {
    Batch lot = lot(BatchStatus.ON_HOLD, "M", "500");
    lot.assignColor(NAVY);
    BatchFinishingStep dyed = outStep(1, DYEING, NAVY);
    dyed.receive(new BigDecimal("495"), LocalDate.of(2026, 9, 1), null, null, null, null);
    BatchFinishingStep finish = outStep(2, MERCERISING, null);
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, finish.getId()))
        .thenReturn(Optional.of(finish));
    when(stepRepository.findByTenantIdAndBatchIdAndIsActiveTrueOrderBySequenceAsc(TENANT, BATCH_ID))
        .thenReturn(List.of(dyed, finish));

    service.receive(
        finish.getId(),
        new BatchFinishingDtos.ReceiveRequest(new BigDecimal("490"), null, null, null, null, null));

    assertThat(lot.getColorId()).isEqualTo(NAVY);
    assertThat(lot.getStatus()).isEqualTo(BatchStatus.AVAILABLE);
  }

  @Test
  void aStepThatIsNotOutCannotBeReceived() {
    lot(BatchStatus.AVAILABLE, "M", "500");
    BatchFinishingStep step = outStep(1, MERCERISING, null);
    step.cancel("mistake");
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, step.getId()))
        .thenReturn(Optional.of(step));

    assertThatThrownBy(
            () ->
                service.receive(
                    step.getId(),
                    new BatchFinishingDtos.ReceiveRequest(
                        new BigDecimal("490"), null, null, null, null, null)))
        .isInstanceOf(BatchDomainException.class);
  }

  // ── cancel ─────────────────────────────────────────────────────────────────

  @Test
  void cancellingReleasesTheLotWithoutTouchingItsColour() {
    Batch lot = lot(BatchStatus.ON_HOLD, "M", "500");
    BatchFinishingStep step = outStep(1, DYEING, NAVY);
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, step.getId()))
        .thenReturn(Optional.of(step));

    BatchFinishingDtos.StepDto dto =
        service.cancel(step.getId(), new BatchFinishingDtos.CancelRequest("Wrong lot"));

    assertThat(dto.status()).isEqualTo(BatchFinishingStepStatus.CANCELLED);
    assertThat(lot.getStatus()).isEqualTo(BatchStatus.AVAILABLE);
    assertThat(lot.getColorId()).isNull();
  }

  // ── history entered for a lot bought finished ──────────────────────────────

  @Test
  void aBoughtFinishedLotGetsItsHistoryWithoutBeingHeld() {
    Batch lot = lot(BatchStatus.AVAILABLE, "M", "2000");
    when(stepRepository.existsByTenantIdAndBatchIdAndStatusAndIsActiveTrue(
            TENANT, BATCH_ID, BatchFinishingStepStatus.SENT))
        .thenReturn(false);
    BatchFinishingStep[] saved = new BatchFinishingStep[1];
    when(stepRepository.save(any(BatchFinishingStep.class)))
        .thenAnswer(
            invocation -> {
              saved[0] = invocation.getArgument(0);
              return saved[0];
            });
    when(stepRepository.findByTenantIdAndBatchIdAndIsActiveTrueOrderBySequenceAsc(TENANT, BATCH_ID))
        .thenAnswer(invocation -> List.of(saved[0]));

    BatchFinishingDtos.StepDto dto =
        service.recordDone(
            BATCH_ID,
            new BatchFinishingDtos.RecordDoneRequest(
                DYEING.id(),
                null,
                "SUP-7",
                new BigDecimal("2000"),
                LocalDate.of(2026, 8, 1),
                new BigDecimal("1980"),
                LocalDate.of(2026, 8, 10),
                NAVY,
                null,
                null,
                "Bought dyed"));

    assertThat(dto.status()).isEqualTo(BatchFinishingStepStatus.DONE);
    assertThat(lot.getStatus()).isEqualTo(BatchStatus.AVAILABLE);
    assertThat(lot.getColorId()).isEqualTo(NAVY);
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void anOlderHistoryEntryDoesNotReplaceANewerColour() {
    Batch lot = lot(BatchStatus.AVAILABLE, "M", "2000");
    lot.assignColor(RED);
    BatchFinishingStep newer = outStep(1, DYEING, RED);
    newer.receive(new BigDecimal("1990"), LocalDate.of(2026, 10, 1), null, null, null, null);
    BatchFinishingStep[] saved = new BatchFinishingStep[1];
    when(stepRepository.save(any(BatchFinishingStep.class)))
        .thenAnswer(
            invocation -> {
              saved[0] = invocation.getArgument(0);
              return saved[0];
            });
    when(stepRepository.findByTenantIdAndBatchIdAndIsActiveTrueOrderBySequenceAsc(TENANT, BATCH_ID))
        .thenAnswer(invocation -> List.of(newer, saved[0]));

    service.recordDone(
        BATCH_ID,
        new BatchFinishingDtos.RecordDoneRequest(
            DYEING.id(),
            null,
            null,
            new BigDecimal("2000"),
            LocalDate.of(2026, 8, 1),
            new BigDecimal("1980"),
            LocalDate.of(2026, 8, 10),
            NAVY,
            null,
            null,
            null));

    assertThat(lot.getColorId()).isEqualTo(RED);
  }

  @Test
  void historyCannotBeAddedWhileTheLotIsOut() {
    lot(BatchStatus.ON_HOLD, "M", "2000");
    when(stepRepository.existsByTenantIdAndBatchIdAndStatusAndIsActiveTrue(
            TENANT, BATCH_ID, BatchFinishingStepStatus.SENT))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                service.recordDone(
                    BATCH_ID,
                    new BatchFinishingDtos.RecordDoneRequest(
                        MERCERISING.id(),
                        null,
                        null,
                        new BigDecimal("2000"),
                        LocalDate.of(2026, 8, 1),
                        new BigDecimal("1980"),
                        LocalDate.of(2026, 8, 10),
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("is out for a step");
  }

  // ── reading ────────────────────────────────────────────────────────────────

  @Test
  void historyListsTheStepsAndWhatTheLotHasBecome() {
    Batch lot = lot(BatchStatus.ON_HOLD, "M", "500");
    BatchFinishingStep pretreated = outStep(1, MERCERISING, null);
    pretreated.receive(new BigDecimal("495"), LocalDate.of(2026, 9, 1), null, null, null, null);
    BatchFinishingStep dyed = outStep(2, DYEING, NAVY);
    dyed.receive(new BigDecimal("490"), LocalDate.of(2026, 9, 10), null, null, null, null);
    BatchFinishingStep open = outStep(3, MERCERISING, null);
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT)).thenReturn(Optional.of(lot));
    when(stepRepository.findByTenantIdAndBatchIdAndIsActiveTrueOrderBySequenceAsc(TENANT, BATCH_ID))
        .thenReturn(List.of(pretreated, dyed, open));

    BatchFinishingDtos.HistoryDto history = service.history(BATCH_ID);

    assertThat(history.out()).isTrue();
    assertThat(history.steps()).hasSize(3);
    assertThat(history.appliedCategories())
        .containsExactly(FabricFinishingCategory.PRETREATMENT, FabricFinishingCategory.DYEING);
  }

  @Test
  void searchingByJobNumberMatchesAnywhereAndEscapesWildcards() {
    when(stepRepository.search(any(), any(), any(), any(), any())).thenReturn(List.of());

    service.search(null, null, " DH_4%1 ");

    ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
    verify(stepRepository)
        .search(
            any(UUID.class), any(BatchFinishingStepStatus.class), any(), pattern.capture(), any());
    assertThat(pattern.getValue()).isEqualTo("%dh\\_4\\%1%");
  }

  @Test
  void searchingWithoutAJobNumberMatchesEverything() {
    when(stepRepository.search(any(), any(), any(), any(), any())).thenReturn(List.of());

    service.search(BatchFinishingStepStatus.SENT, PARTNER, "  ");

    verify(stepRepository)
        .search(
            any(UUID.class),
            any(BatchFinishingStepStatus.class),
            any(UUID.class),
            org.mockito.ArgumentMatchers.eq("%"),
            any());
  }

  // ── fixtures ───────────────────────────────────────────────────────────────

  private Batch lot(BatchStatus status, String unit, String quantity) {
    Batch lot =
        Batch.builder()
            .productId(PRODUCT_ID)
            .productType(ProductType.FABRIC)
            .batchCode("LOT-1")
            .quantity(new BigDecimal(quantity))
            .unit(unit)
            .status(status)
            .build();
    lot.setId(BATCH_ID);
    lot.setTenantId(TENANT);
    lot.setIsActive(true);
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT))
        .thenReturn(Optional.of(lot));
    return lot;
  }

  private static BatchFinishingStep outStep(
      int sequence, FabricFinishingProcessDto process, UUID colour) {
    BatchFinishingStep step =
        BatchFinishingStep.send(
            TENANT,
            BATCH_ID,
            sequence,
            process.id(),
            PARTNER,
            "J-" + sequence,
            new BigDecimal("3000"),
            LocalDate.of(2026, 8, 1).plusDays(sequence),
            colour,
            null);
    step.setId(UUID.randomUUID());
    return step;
  }

  private static BatchFinishingDtos.SendRequest send(
      FabricFinishingProcessDto process, UUID partner, String jobNumber, UUID colour) {
    return new BatchFinishingDtos.SendRequest(process.id(), partner, jobNumber, null, colour, null);
  }

  private static FabricFinishingProcessDto process(
      String code, String name, FabricFinishingCategory category) {
    return new FabricFinishingProcessDto(
        UUID.randomUUID(),
        code,
        name,
        null,
        null,
        category,
        FinishingChargeUnit.M,
        category.colours(),
        category.affectsLabel(),
        true,
        true);
  }

  private static ColorQueryService.ColorReference colour(UUID id) {
    return new ColorQueryService.ColorReference(id, "C-" + id, "Colour", "#112233", true);
  }
}
