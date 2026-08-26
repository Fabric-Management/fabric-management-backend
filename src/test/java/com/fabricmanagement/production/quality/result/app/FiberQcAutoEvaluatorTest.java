package com.fabricmanagement.production.quality.result.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FiberQcAutoEvaluatorTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final UUID STANDARD_ID = UUID.randomUUID();

  @Mock private FiberQualityQueryService fiberQualityQueryService;
  @Mock private Batch batch;
  @Mock private Fiber fiber;

  @InjectMocks private FiberQcAutoEvaluator evaluator;

  @BeforeEach
  void setUpEvaluationPath() {
    when(batch.getProductType()).thenReturn(ProductType.FIBER);
    when(batch.getProductId()).thenReturn(PRODUCT_ID);
    when(batch.getQualityStandardId()).thenReturn(STANDARD_ID);
    when(fiberQualityQueryService.findByProductIdOrId(PRODUCT_ID)).thenReturn(Optional.of(fiber));
  }

  @ParameterizedTest
  @CsvSource({"4.0,APPROVED", "4.5,CONDITIONAL_ACCEPT", "6.0,REJECTED"})
  void preservesExistingDecisionWhenUniformityBoundsAreNull(
      double fineness, TestApprovalStatus expected) {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .finenessMin(3.0)
            .finenessTarget(4.0)
            .finenessMax(5.0)
            .build();
    FiberTestResult result =
        FiberTestResult.builder().fineness(fineness).uniformityIndex(84.0).build();

    assertThat(evaluate(result, standard)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"79.9,REJECTED", "82.0,CONDITIONAL_ACCEPT", "84.0,APPROVED"})
  void evaluatesUniformityIndexAgainstConfiguredBounds(
      double uniformityIndex, TestApprovalStatus expected) {
    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .uniformityIndexMin(80.0)
            .uniformityIndexTarget(84.0)
            .uniformityIndexMax(86.0)
            .build();
    FiberTestResult result = FiberTestResult.builder().uniformityIndex(uniformityIndex).build();

    assertThat(evaluate(result, standard)).isEqualTo(expected);
  }

  private TestApprovalStatus evaluate(FiberTestResult result, FiberQualityStandard standard) {
    when(fiberQualityQueryService.findQualityStandardById(TENANT_ID, STANDARD_ID))
        .thenReturn(Optional.of(standard));

    return evaluator.evaluate(result, batch, TENANT_ID).approvalStatus();
  }
}
