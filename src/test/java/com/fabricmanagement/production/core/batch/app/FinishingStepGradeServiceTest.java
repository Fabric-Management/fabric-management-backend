package com.fabricmanagement.production.core.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStepGrade;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeBreakdown;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeMetres;
import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.core.batch.dto.BatchFinishingDtos;
import com.fabricmanagement.production.core.batch.infra.repository.BatchFinishingStepGradeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinishingStepGradeServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID STEP = UUID.randomUUID();

  private static final QualityGradeReference FIRST = grade("1A", "First quality", 1, true, true);
  private static final QualityGradeReference BAD = grade("B", "Defective", 3, false, true);
  private static final QualityGradeReference RETIRED = grade("2B", "Retired", 2, true, false);

  @Mock private BatchFinishingStepGradeRepository rows;
  @Mock private QualityGradeQueryService qualityGrades;

  private FinishingStepGradeService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service = new FinishingStepGradeService(rows, qualityGrades);
    when(qualityGrades.findSalesGradeReferences(ProductType.FABRIC, true))
        .thenReturn(List.of(FIRST, BAD));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static QualityGradeReference grade(
      String code, String name, int rank, boolean saleable, boolean active) {
    return new QualityGradeReference(
        UUID.randomUUID(), code, name, rank, BigDecimal.ONE, saleable, active);
  }

  private static BatchFinishingDtos.GradeMetresRequest request(
      QualityGradeReference grade, String metres) {
    return new BatchFinishingDtos.GradeMetresRequest(grade.id(), new BigDecimal(metres));
  }

  // ── checked ────────────────────────────────────────────────────────────────

  @Test
  void noSplitIsFineAndAsksNothingOfTheGradeCatalogue() {
    assertThat(service.checked(null, new BigDecimal("2940")).isEmpty()).isTrue();
    assertThat(service.checked(List.of(), new BigDecimal("2940")).isEmpty()).isTrue();

    verifyNoInteractions(qualityGrades);
  }

  @Test
  void aSplitThatAddsUpAndUsesActiveFabricGradesIsAccepted() {
    FinishingGradeBreakdown breakdown =
        service.checked(
            List.of(request(FIRST, "2800"), request(BAD, "140")), new BigDecimal("2940"));

    assertThat(breakdown.rows()).hasSize(2);
    assertThat(breakdown.total()).isEqualByComparingTo("2940");
  }

  @Test
  void aSplitThatDoesNotAddUpIsRejectedBeforeTheGradesAreLookedUp() {
    assertThatThrownBy(
            () ->
                service.checked(
                    List.of(request(FIRST, "2800"), request(BAD, "100")), new BigDecimal("2940")))
        .isInstanceOf(BatchDomainException.class)
        .extracting(error -> ((BatchDomainException) error).getErrorCode())
        .isEqualTo("FINISHING_GRADE_METRES_MISMATCH");

    verifyNoInteractions(qualityGrades);
  }

  @Test
  void aGradeThatIsNotAnActiveFabricGradeOfTheTenantIsNotFound() {
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                service.checked(
                    List.of(
                        request(FIRST, "2800"),
                        new BatchFinishingDtos.GradeMetresRequest(unknown, new BigDecimal("140"))),
                    new BigDecimal("2940")))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(unknown.toString());
  }

  // ── save ───────────────────────────────────────────────────────────────────

  @Test
  void savingWritesOneRowPerGradeForTheStep() {
    FinishingGradeBreakdown breakdown =
        FinishingGradeBreakdown.of(
            List.of(
                new FinishingGradeMetres(FIRST.id(), new BigDecimal("2800")),
                new FinishingGradeMetres(BAD.id(), new BigDecimal("140"))));

    service.save(TENANT, STEP, breakdown);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<BatchFinishingStepGrade>> saved = ArgumentCaptor.forClass(List.class);
    verify(rows).saveAll(saved.capture());
    assertThat(saved.getValue()).hasSize(2);
    assertThat(saved.getValue())
        .allSatisfy(
            row -> {
              assertThat(row.getTenantId()).isEqualTo(TENANT);
              assertThat(row.getStepId()).isEqualTo(STEP);
            });
    assertThat(saved.getValue().get(0).getQualityGradeId()).isEqualTo(FIRST.id());
    assertThat(saved.getValue().get(0).getMetres()).isEqualByComparingTo("2800");
  }

  @Test
  void savingNoSplitWritesNothing() {
    service.save(TENANT, STEP, FinishingGradeBreakdown.none());

    verify(rows, never()).saveAll(any());
  }

  // ── forSteps ───────────────────────────────────────────────────────────────

  @Test
  void aStepsSplitIsReadBestGradeFirstEvenForARetiredGrade() {
    UUID otherStep = UUID.randomUUID();
    when(rows.findByTenantIdAndStepIdInAndIsActiveTrue(eq(TENANT), anyCollection()))
        .thenReturn(
            List.of(
                stored(STEP, BAD, "140"),
                stored(STEP, FIRST, "2700"),
                stored(STEP, RETIRED, "100"),
                stored(otherStep, FIRST, "50")));
    when(qualityGrades.findReferencesByIds(anyCollection()))
        .thenReturn(List.of(FIRST, BAD, RETIRED));

    Map<UUID, List<BatchFinishingDtos.StepGradeDto>> byStep =
        service.forSteps(TENANT, List.of(STEP, otherStep));

    assertThat(byStep.get(STEP))
        .extracting(BatchFinishingDtos.StepGradeDto::qualityGradeCode)
        .containsExactly("1A", "2B", "B");
    assertThat(byStep.get(STEP).get(1).qualityGradeName()).isEqualTo("Retired");
    assertThat(byStep.get(otherStep)).hasSize(1);
  }

  @Test
  void aStepWithoutASplitHasNoEntryAndNoStepsAskNothing() {
    when(rows.findByTenantIdAndStepIdInAndIsActiveTrue(eq(TENANT), anyCollection()))
        .thenReturn(List.of());

    assertThat(service.forSteps(TENANT, List.of(STEP))).isEmpty();
    assertThat(service.forSteps(TENANT, List.of())).isEmpty();
    verify(rows).findByTenantIdAndStepIdInAndIsActiveTrue(eq(TENANT), anyCollection());
  }

  private static BatchFinishingStepGrade stored(
      UUID step, QualityGradeReference grade, String metres) {
    return BatchFinishingStepGrade.record(
        TENANT, step, new FinishingGradeMetres(grade.id(), new BigDecimal(metres)));
  }
}
