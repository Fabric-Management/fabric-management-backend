package com.fabricmanagement.production.core.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStep;
import com.fabricmanagement.production.core.batch.domain.FinishingCostLineStatus;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeBreakdown;
import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.core.batch.dto.BatchFinishingDtos;
import com.fabricmanagement.production.core.batch.infra.repository.BatchFinishingStepRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchFinishingCostServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID BATCH = UUID.randomUUID();
  private static final UUID PROCESS = UUID.randomUUID();
  private static final UUID PARTNER = UUID.randomUUID();
  private static final UUID TIER = UUID.randomUUID();
  private static final LocalDate SENT_ON = LocalDate.of(2026, 10, 1);

  @Mock private BatchFinishingStepRepository stepRepository;
  @Mock private BatchFinishingService finishing;
  @Mock private FinishingStepGradeService stepGrades;

  private BatchFinishingCostService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service = new BatchFinishingCostService(stepRepository, finishing, stepGrades);
    when(stepGrades.checked(any(), any())).thenReturn(FinishingGradeBreakdown.none());
    when(stepGrades.references(any())).thenReturn(Map.of());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static BatchFinishingStep outStep() {
    BatchFinishingStep step =
        BatchFinishingStep.send(
            TENANT,
            BATCH,
            1,
            PROCESS,
            PARTNER,
            "DH-1",
            new BigDecimal("3000"),
            SENT_ON,
            UUID.randomUUID(),
            null);
    step.assignPriceClass(TIER);
    step.setId(UUID.randomUUID());
    return step;
  }

  private static FabricFinishingPriceDto generalPrice() {
    return new FabricFinishingPriceDto(
        UUID.randomUUID(),
        PROCESS,
        "DYEING_REACTIVE",
        "Reactive dyeing",
        FabricFinishingCategory.DYEING,
        PARTNER,
        FinishingBillingBasis.RETURNED,
        null,
        null,
        null,
        TIER,
        "DARK",
        "Dark",
        null,
        null,
        null,
        null,
        FinishingChargeUnit.M,
        new BigDecimal("1.85"),
        "GBP",
        SENT_ON);
  }

  private static BatchFinishingDtos.CostSuggestionRequest request(LocalDate priceDate) {
    return new BatchFinishingDtos.CostSuggestionRequest(new BigDecimal("2940"), null, priceDate);
  }

  @Test
  void pricesTheDeliveryOfALotThatIsOutAtTheTariffOfTheDayItWasSent() {
    BatchFinishingStep step = outStep();
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, step.getId()))
        .thenReturn(Optional.of(step));
    when(finishing.prices(BATCH, PROCESS, PARTNER, TIER, SENT_ON))
        .thenReturn(List.of(generalPrice()));

    BatchFinishingDtos.CostSuggestionDto suggestion = service.suggest(step.getId(), request(null));

    assertThat(suggestion.priceDate()).isEqualTo(SENT_ON);
    assertThat(suggestion.complete()).isTrue();
    assertThat(suggestion.lines().get(0).status()).isEqualTo(FinishingCostLineStatus.PRICED);
    assertThat(suggestion.totals().get(0).amount()).isEqualByComparingTo("5439.00");
    assertThat(step.isOut()).as("a suggestion changes nothing").isTrue();
  }

  @Test
  void aGivenDayIsUsedForThePrices() {
    BatchFinishingStep step = outStep();
    LocalDate later = LocalDate.of(2026, 10, 15);
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, step.getId()))
        .thenReturn(Optional.of(step));
    when(finishing.prices(BATCH, PROCESS, PARTNER, TIER, later)).thenReturn(List.of());

    BatchFinishingDtos.CostSuggestionDto suggestion = service.suggest(step.getId(), request(later));

    assertThat(suggestion.priceDate()).isEqualTo(later);
    assertThat(suggestion.complete()).isFalse();
    verify(finishing).prices(BATCH, PROCESS, PARTNER, TIER, later);
  }

  @Test
  void aStepThatIsNoLongerOutHasNothingToPrice() {
    BatchFinishingStep step = outStep();
    step.receive(new BigDecimal("2940"), LocalDate.of(2026, 10, 9), null, null, null, null);
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, step.getId()))
        .thenReturn(Optional.of(step));

    assertThatThrownBy(() -> service.suggest(step.getId(), request(null)))
        .isInstanceOf(BatchDomainException.class)
        .extracting(error -> ((BatchDomainException) error).getErrorCode())
        .isEqualTo("FINISHING_STEP_NOT_OUT");
    verify(finishing, never()).prices(any(), any(), any(), any(), any());
  }

  @Test
  void anUnknownStepIsNotFound() {
    UUID unknown = UUID.randomUUID();
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, unknown))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.suggest(unknown, request(null)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void aRejectedSplitStopsBeforeAnyPriceIsRead() {
    BatchFinishingStep step = outStep();
    when(stepRepository.findByTenantIdAndIdAndIsActiveTrue(TENANT, step.getId()))
        .thenReturn(Optional.of(step));
    when(stepGrades.checked(any(), any()))
        .thenThrow(
            new BatchDomainException("Does not add up", "FINISHING_GRADE_METRES_MISMATCH", 422));

    assertThatThrownBy(() -> service.suggest(step.getId(), request(null)))
        .isInstanceOf(BatchDomainException.class);
    verify(finishing, never()).prices(any(), any(), any(), any(), any());
  }
}
