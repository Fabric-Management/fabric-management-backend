package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.TaskLabelAssignmentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskLabelRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PriorityScoreCalculator Unit Tests")
class PriorityScoreCalculatorTest {

  @Mock private TaskLabelAssignmentRepository labelAssignmentRepo;
  @Mock private TaskLabelRepository labelRepo;
  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-03-18T12:00:00Z"), ZoneId.of("UTC"));

  @InjectMocks private PriorityScoreCalculator calculator;

  private static final UUID BOARD_ID = UUID.randomUUID();
  private static final LocalDate TODAY = LocalDate.of(2026, 3, 18);

  // =========================================================================
  // DEADLINE TESTLERİ
  // =========================================================================

  @Nested
  @DisplayName("Deadline bazlı hesaplamalar")
  class DeadlineTests {

    @Test
    @DisplayName("Deadline geçtiyse Integer.MAX_VALUE döner")
    void should_return_max_when_deadline_passed() {
      when(labelAssignmentRepo.findAllByTaskId(any())).thenReturn(List.of());

      var task = buildTask(TaskType.GENERAL, Priority.LOW, TODAY.minusDays(1));

      int score = calculator.calculate(task);

      assertThat(score).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Bugün deadline → Integer.MAX_VALUE döner")
    void should_return_max_when_deadline_is_today() {
      when(labelAssignmentRepo.findAllByTaskId(any())).thenReturn(List.of());

      var task = buildTask(TaskType.GENERAL, Priority.LOW, TODAY);
      // isOverdue() → deadline < today olduğunda true ama bugün için false
      // daysUntilDeadline = 0 → ≤ 0 → MAX_VALUE
      int score = calculator.calculate(task);

      assertThat(score).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("7 gün sonraki deadline → deadline skoru 0, diğer skorlar eklenir")
    void should_not_add_deadline_score_when_far_away() {
      when(labelAssignmentRepo.findAllByTaskId(any())).thenReturn(List.of());

      var task = buildTask(TaskType.GENERAL, Priority.LOW, TODAY.plusDays(14));

      int score = calculator.calculate(task);

      // deadlineScore = 0 (14 gün sonra), taskTypeScore = 10/GENERAL, priorityScore = 5/LOW
      assertThat(score).isEqualTo(15);
    }
  }

  // =========================================================================
  // TASK TYPE TESTLERİ
  // =========================================================================

  @Nested
  @DisplayName("TaskType bazlı ağırlıklar")
  class TaskTypeTests {

    @Test
    @DisplayName("QUALITY task GENERAL'dan daha yüksek skor alır")
    void should_weight_quality_higher_than_general() {
      when(labelAssignmentRepo.findAllByTaskId(any())).thenReturn(List.of());

      var qualityTask = buildTask(TaskType.QUALITY, Priority.MEDIUM, TODAY.plusDays(14));
      var generalTask = buildTask(TaskType.GENERAL, Priority.MEDIUM, TODAY.plusDays(14));

      int qualityScore = calculator.calculate(qualityTask);
      int generalScore = calculator.calculate(generalTask);

      assertThat(qualityScore).isGreaterThan(generalScore);
    }

    @Test
    @DisplayName("SHIPMENT task PLANNING'den daha yüksek skor alır")
    void should_weight_shipment_higher_than_planning() {
      when(labelAssignmentRepo.findAllByTaskId(any())).thenReturn(List.of());

      var shipmentTask = buildTask(TaskType.SHIPMENT, Priority.MEDIUM, TODAY.plusDays(14));
      var planningTask = buildTask(TaskType.PLANNING, Priority.MEDIUM, TODAY.plusDays(14));

      assertThat(calculator.calculate(shipmentTask))
          .isGreaterThan(calculator.calculate(planningTask));
    }
  }

  // =========================================================================
  // LABEL BONUS TESTLERİ
  // =========================================================================

  @Nested
  @DisplayName("Etiket bonus hesaplamaları")
  class LabelBonusTests {

    @Test
    @DisplayName("VIP_CLIENT etiketi +20 bonus ekler")
    void should_include_vip_client_bonus() {
      var task = buildTask(TaskType.GENERAL, Priority.LOW, TODAY.plusDays(14));
      var labelId = UUID.randomUUID();

      var assignment = mock(com.fabricmanagement.flowboard.task.domain.TaskLabelAssignment.class);
      when(assignment.getLabelId()).thenReturn(labelId);
      when(labelAssignmentRepo.findAllByTaskId(task.getId())).thenReturn(List.of(assignment));

      var vipLabel = TaskLabel.createGlobal("VIP_CLIENT", "#9B59B6", "⭐");
      when(labelRepo.findById(labelId)).thenReturn(Optional.of(vipLabel));

      int scoreWithoutLabel = calculator.calculateWithLabels(task, List.of());
      int scoreWithLabel = calculator.calculateWithLabels(task, List.of("VIP_CLIENT"));

      assertThat(scoreWithLabel - scoreWithoutLabel).isEqualTo(20);
    }

    @Test
    @DisplayName("URGENT etiketi +15 bonus ekler")
    void should_include_urgent_bonus() {
      var task = buildTask(TaskType.GENERAL, Priority.LOW, TODAY.plusDays(14));

      int scoreWithout = calculator.calculateWithLabels(task, List.of());
      int scoreWith = calculator.calculateWithLabels(task, List.of("URGENT"));

      assertThat(scoreWith - scoreWithout).isEqualTo(15);
    }

    @Test
    @DisplayName("Birden fazla etiket bonus'ları toplanır")
    void should_sum_multiple_label_bonuses() {
      var task = buildTask(TaskType.GENERAL, Priority.LOW, TODAY.plusDays(14));

      int scoreWithBoth = calculator.calculateWithLabels(task, List.of("VIP_CLIENT", "URGENT"));
      int scoreWithout = calculator.calculateWithLabels(task, List.of());

      assertThat(scoreWithBoth - scoreWithout).isEqualTo(35); // 20 + 15
    }
  }

  // =========================================================================
  // HELPER
  // =========================================================================

  private Task buildTask(TaskType taskType, Priority priority, LocalDate deadline) {
    return Task.create(
        "TSK-TEST",
        BOARD_ID,
        "Test task",
        taskType,
        ModuleType.GENERAL,
        priority,
        deadline,
        null,
        null,
        null);
  }
}
