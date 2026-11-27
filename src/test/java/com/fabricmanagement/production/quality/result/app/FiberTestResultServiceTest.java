package com.fabricmanagement.production.quality.result.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.quality.result.app.FiberQcAutoEvaluator.EvaluationResult;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Outcome;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Parameter;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.ParameterEvaluation;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.BatchQualityStandardMissingEvent;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.production.quality.result.infra.repository.FiberTestResultRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class FiberTestResultServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();
  private static final UUID TEST_RESULT_ID = UUID.randomUUID();

  @Mock private FiberTestResultRepository testResultRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private FiberQcAutoEvaluator qcAutoEvaluator;
  @Mock private Batch batch;

  @InjectMocks private FiberTestResultService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(USER_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private AtomicReference<FiberTestResult> persistResults() {
    AtomicReference<FiberTestResult> persisted = new AtomicReference<>();
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(testResultRepository.save(any(FiberTestResult.class)))
        .thenAnswer(
            invocation -> {
              FiberTestResult result = invocation.getArgument(0);
              persisted.set(result);
              return result;
            });
    return persisted;
  }

  private static CreateFiberTestResultRequest request() {
    return CreateFiberTestResultRequest.builder()
        .batchId(BATCH_ID)
        .testDate(Instant.now().minusSeconds(60))
        .uniformityIndex(84.0)
        .build();
  }

  @Test
  void createsAndReadsBackUniformityIndex() {
    AtomicReference<FiberTestResult> persisted = persistResults();
    when(qcAutoEvaluator.evaluate(any(FiberTestResult.class), eq(batch), eq(TENANT_ID)))
        .thenReturn(evaluatedUniformity(80.0, Outcome.WITHIN_LIMITS));

    FiberTestResultDto created = service.create(request());
    when(testResultRepository.findByTenantIdAndId(TENANT_ID, TEST_RESULT_ID))
        .thenReturn(Optional.of(persisted.get()));
    Optional<FiberTestResultDto> reloaded = service.getById(TEST_RESULT_ID);

    assertThat(created.getUniformityIndex()).isEqualTo(84.0);
    assertThat(created.getApprovalStatus()).isEqualTo(TestApprovalStatus.APPROVED);
    assertThat(reloaded).isPresent();
    assertThat(reloaded.orElseThrow().getUniformityIndex()).isEqualTo(84.0);
    assertThat(created.getEvaluation())
        .isNotNull()
        .isEqualTo(reloaded.orElseThrow().getEvaluation());
    verify(applicationEventPublisher, never())
        .publishEvent(any(BatchQualityStandardMissingEvent.class));
  }

  @Test
  void aResultKeepsTheBaleItNamesAndRefusesABaleOfAnotherLot() {
    AtomicReference<FiberTestResult> persisted = persistResults();
    when(qcAutoEvaluator.evaluate(any(FiberTestResult.class), eq(batch), eq(TENANT_ID)))
        .thenReturn(evaluatedUniformity(80.0, Outcome.WITHIN_LIMITS));

    UUID baleId = UUID.randomUUID();
    StockUnit bale = mock(StockUnit.class);
    when(bale.getBatchId()).thenReturn(BATCH_ID);
    when(stockUnitRepository.findByIdAndTenantIdAndIsActiveTrue(baleId, TENANT_ID))
        .thenReturn(Optional.of(bale));
    var withBale = request();
    withBale.setStockUnitId(baleId);

    FiberTestResultDto named = service.create(withBale);

    assertThat(named.getBatchId()).isEqualTo(BATCH_ID);
    assertThat(named.getStockUnitId()).isEqualTo(baleId);
    assertThat(persisted.get().getStockUnitId()).isEqualTo(baleId);
    FiberTestResultDto unnamed = service.create(request());
    assertThat(unnamed.getBatchId()).isEqualTo(BATCH_ID);
    assertThat(unnamed.getStockUnitId()).isNull();
    assertThat(persisted.get().getStockUnitId()).isNull();

    UUID otherBaleId = UUID.randomUUID();
    StockUnit otherBale = mock(StockUnit.class);
    when(otherBale.getBatchId()).thenReturn(UUID.randomUUID());
    when(stockUnitRepository.findByIdAndTenantIdAndIsActiveTrue(otherBaleId, TENANT_ID))
        .thenReturn(Optional.of(otherBale));
    var wrongLot = request();
    wrongLot.setStockUnitId(otherBaleId);

    assertThatThrownBy(() -> service.create(wrongLot))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Provided batchId does not match StockUnit's batch");
    verify(testResultRepository, times(2)).save(any(FiberTestResult.class));
  }

  @ParameterizedTest
  @CsvSource({"80.0,WITHIN_LIMITS,APPROVED", "85.0,OUTSIDE_LIMITS,REJECTED", ",NO_LIMIT,PENDING"})
  void automaticEvaluationDoesNotPublishAnApprovalEvent(
      Double min, Outcome outcome, TestApprovalStatus status) {
    persistResults();
    when(qcAutoEvaluator.evaluate(any(FiberTestResult.class), eq(batch), eq(TENANT_ID)))
        .thenReturn(evaluatedUniformity(min, outcome));

    assertThat(service.create(request()).getApprovalStatus()).isEqualTo(status);
    verify(applicationEventPublisher, never())
        .publishEvent(any(FiberTestResultApprovedEvent.class));
  }

  @Test
  void aBlendWithoutApplicableProfileStaysPendingAndAsksForManualReviewWithTheBlendLabel() {
    persistResults();
    when(qcAutoEvaluator.evaluate(any(FiberTestResult.class), eq(batch), eq(TENANT_ID)))
        .thenReturn(missingProfile("CO 60% / PES 40%", "NO_APPLICABLE_QUALITY_PROFILE"));

    FiberTestResultDto created = service.create(request());

    assertThat(created.getApprovalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(created.getEvaluation().diagnosticCode()).isEqualTo("NO_APPLICABLE_QUALITY_PROFILE");
    BatchQualityStandardMissingEvent published = publishedMissingStandard();
    assertThat(published.getBatchId()).isEqualTo(BATCH_ID);
    assertThat(published.getTarget()).isEqualTo("CO 60% / PES 40%");
    // A diagnostic the wording does not know reads as the general reason.
    assertThat(published.getReasonCode()).isEqualTo("NO_QUALITY_PROFILE");
  }

  @Test
  void aBatchWithoutFibreDefinitionFallsBackToItsBatchCodeInTheEvent() {
    persistResults();
    when(batch.getBatchCode()).thenReturn("FIB-2026-0001");
    when(qcAutoEvaluator.evaluate(any(FiberTestResult.class), eq(batch), eq(TENANT_ID)))
        .thenReturn(missingProfile(null, "FIBER_PRODUCT_NOT_FOUND"));

    service.create(request());

    BatchQualityStandardMissingEvent published = publishedMissingStandard();
    assertThat(published.getTarget()).isEqualTo("FIB-2026-0001");
    assertThat(published.getBatchCode()).isEqualTo("FIB-2026-0001");
    assertThat(published.getReasonCode()).isEqualTo("FIBER_PRODUCT_NOT_FOUND");
  }

  private static EvaluationResult evaluatedUniformity(Double min, Outcome outcome) {
    return new EvaluationResult(
        new FiberTestEvaluation(
            UUID.randomUUID(),
            "Alder Beck cotton",
            null,
            true,
            min != null,
            outcome == Outcome.OUTSIDE_LIMITS,
            List.of(
                new ParameterEvaluation(
                    Parameter.UNIFORMITY_INDEX, 84.0, min, null, 84.0, 0.0, outcome))),
        "Cotton (100%)");
  }

  private static EvaluationResult missingProfile(String label, String diagnostic) {
    return new EvaluationResult(
        new FiberTestEvaluation(null, null, diagnostic, false, false, false, List.of()), label);
  }

  private BatchQualityStandardMissingEvent publishedMissingStandard() {
    ArgumentCaptor<BatchQualityStandardMissingEvent> captor =
        ArgumentCaptor.forClass(BatchQualityStandardMissingEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    return captor.getValue();
  }
}
