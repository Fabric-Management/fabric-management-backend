package com.fabricmanagement.production.quality.result.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
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
  @Mock private InAppNotificationService notificationService;
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

  @Test
  void createsAndReadsBackUniformityIndex() {
    AtomicReference<FiberTestResult> persisted = new AtomicReference<>();
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(testResultRepository.save(any(FiberTestResult.class)))
        .thenAnswer(
            invocation -> {
              FiberTestResult result = invocation.getArgument(0);
              persisted.set(result);
              return result;
            });
    when(qcAutoEvaluator.evaluate(any(FiberTestResult.class), eq(batch), eq(TENANT_ID)))
        .thenReturn(
            new FiberQcAutoEvaluator.EvaluationResult(TestApprovalStatus.APPROVED, true, "CO"));
    CreateFiberTestResultRequest request =
        CreateFiberTestResultRequest.builder()
            .batchId(BATCH_ID)
            .testDate(Instant.now().minusSeconds(60))
            .uniformityIndex(84.0)
            .build();

    FiberTestResultDto created = service.create(request);
    when(testResultRepository.findByTenantIdAndId(TENANT_ID, TEST_RESULT_ID))
        .thenReturn(Optional.of(persisted.get()));
    Optional<FiberTestResultDto> reloaded = service.getById(TEST_RESULT_ID);

    assertThat(created.getUniformityIndex()).isEqualTo(84.0);
    assertThat(reloaded).isPresent();
    assertThat(reloaded.orElseThrow().getUniformityIndex()).isEqualTo(84.0);
  }
}
