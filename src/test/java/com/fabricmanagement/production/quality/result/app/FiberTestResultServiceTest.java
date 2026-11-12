package com.fabricmanagement.production.quality.result.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.BatchQualityStandardMissingEvent;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.production.quality.result.infra.repository.FiberTestResultRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        .thenReturn(
            new FiberQcAutoEvaluator.EvaluationResult(
                TestApprovalStatus.APPROVED, true, "Cotton (100%)", null));

    FiberTestResultDto created = service.create(request());
    when(testResultRepository.findByTenantIdAndId(TENANT_ID, TEST_RESULT_ID))
        .thenReturn(Optional.of(persisted.get()));
    Optional<FiberTestResultDto> reloaded = service.getById(TEST_RESULT_ID);

    assertThat(created.getUniformityIndex()).isEqualTo(84.0);
    assertThat(created.getApprovalStatus()).isEqualTo(TestApprovalStatus.APPROVED);
    assertThat(reloaded).isPresent();
    assertThat(reloaded.orElseThrow().getUniformityIndex()).isEqualTo(84.0);
    verify(applicationEventPublisher, never())
        .publishEvent(any(BatchQualityStandardMissingEvent.class));
  }

  @Test
  void aBlendWithoutApplicableProfileStaysPendingAndAsksForManualReviewWithTheBlendLabel() {
    persistResults();
    when(qcAutoEvaluator.evaluate(any(FiberTestResult.class), eq(batch), eq(TENANT_ID)))
        .thenReturn(
            new FiberQcAutoEvaluator.EvaluationResult(
                TestApprovalStatus.PENDING,
                false,
                "CO 60% / PES 40%",
                "NO_APPLICABLE_QUALITY_PROFILE"));

    FiberTestResultDto created = service.create(request());

    assertThat(created.getApprovalStatus()).isNotEqualTo(TestApprovalStatus.APPROVED);
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
        .thenReturn(
            new FiberQcAutoEvaluator.EvaluationResult(
                TestApprovalStatus.PENDING, false, null, "FIBER_PRODUCT_NOT_FOUND"));

    service.create(request());

    BatchQualityStandardMissingEvent published = publishedMissingStandard();
    assertThat(published.getTarget()).isEqualTo("FIB-2026-0001");
    assertThat(published.getBatchCode()).isEqualTo("FIB-2026-0001");
    assertThat(published.getReasonCode()).isEqualTo("FIBER_PRODUCT_NOT_FOUND");
  }

  private BatchQualityStandardMissingEvent publishedMissingStandard() {
    ArgumentCaptor<BatchQualityStandardMissingEvent> captor =
        ArgumentCaptor.forClass(BatchQualityStandardMissingEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    return captor.getValue();
  }
}
