package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.dto.CreateTaskRequest;
import com.fabricmanagement.flowboard.task.dto.UpdateTaskStatusRequest;
import com.fabricmanagement.flowboard.task.infra.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskService Unit Tests")
class TaskServiceTest {

  @Mock private TaskRepository taskRepo;
  @Mock private TaskAssigneeRepository assigneeRepo;
  @Mock private TaskLabelAssignmentRepository taskLabelAssignmentRepo;
  @Mock private TaskLabelRepository taskLabelRepo;
  @Mock private BoardRepository boardRepo;
  @Mock private PriorityScoreCalculator scoreCalculator;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private com.fabricmanagement.platform.user.api.facade.UserFacade userFacade;

  @InjectMocks private TaskService taskService;

  private static final UUID BOARD_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID TASK_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    // Default stub'lar
    when(boardRepo.findById(BOARD_ID))
        .thenReturn(Optional.of(mock(com.fabricmanagement.flowboard.board.domain.Board.class)));
    when(scoreCalculator.calculateWithLabels(any(), any())).thenReturn(50);
    when(scoreCalculator.calculate(any())).thenReturn(50);
    when(taskRepo.getNextTaskNumber()).thenReturn(1L);
    when(taskLabelAssignmentRepo.findAllByTaskIdIn(any())).thenReturn(java.util.List.of());
    doNothing().when(eventPublisher).publish(any());
    // UserDto stub: wipLimit = 5 (DEFAULT_WIP_LIMIT) olarak set edilmiş
    com.fabricmanagement.platform.user.dto.UserDto userDto =
        com.fabricmanagement.platform.user.dto.UserDto.builder().wipLimit(5).build();
    when(userFacade.findById(any(), any())).thenReturn(java.util.Optional.of(userDto));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  // =========================================================================
  // STATUS GEÇİŞİ TESTLERİ
  // =========================================================================

  @Nested
  @DisplayName("updateStatus() — durum geçişleri")
  class StatusTransitionTests {

    @Test
    @DisplayName("BACKLOG → IN_PROGRESS geçişinde startedAt set edilir")
    void should_set_started_at_on_first_in_progress() {
      var task =
          Task.create(
              "TSK-0001",
              BOARD_ID,
              "Test task",
              TaskType.PLANNING,
              ModuleType.FIBER,
              Priority.MEDIUM,
              LocalDate.now().plusDays(5),
              null,
              null,
              null);
      task.startTodo(); // önce TO_DO'ya geç
      when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
      when(taskRepo.countInProgressByUser(USER_ID)).thenReturn(0L);
      when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

      taskService.updateStatus(
          TASK_ID, new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS), USER_ID, false);

      assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
      assertThat(task.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("DONE geçişinde completedAt set edilir")
    void should_set_completed_at_on_done() {
      var task =
          Task.create(
              "TSK-0002",
              BOARD_ID,
              "Test task",
              TaskType.QUALITY,
              ModuleType.FIBER,
              Priority.HIGH,
              LocalDate.now().plusDays(3),
              null,
              null,
              null);
      task.startTodo();
      task.startProgress();
      when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
      when(taskRepo.countInProgressByUser(USER_ID)).thenReturn(1L);
      when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

      taskService.updateStatus(
          TASK_ID, new UpdateTaskStatusRequest(TaskStatus.DONE), USER_ID, false);

      assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
      assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("DONE → IN_PROGRESS (reopen) geçişinde completedAt temizlenir")
    void should_clear_completed_at_on_reopen() {
      var task =
          Task.create(
              "TSK-0003",
              BOARD_ID,
              "Test task",
              TaskType.PRODUCTION,
              ModuleType.YARN,
              Priority.MEDIUM,
              LocalDate.now().plusDays(7),
              null,
              null,
              null);
      task.startTodo();
      task.startProgress();
      task.markDone();
      assertThat(task.getCompletedAt()).isNotNull();

      when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
      when(taskRepo.countInProgressByUser(USER_ID)).thenReturn(0L);
      when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

      taskService.updateStatus(
          TASK_ID, new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS), USER_ID, false);

      assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
      assertThat(task.getCompletedAt()).isNull(); // temizlendi
    }
  }

  // =========================================================================
  // WIP LİMİTİ TESTLERİ
  // =========================================================================

  @Nested
  @DisplayName("updateStatus() — WIP limiti kontrolü")
  class WipLimitTests {

    @Test
    @DisplayName("WIP limiti doluyken SELF → WipLimitExceededException fırlatır")
    void should_throw_when_wip_limit_exceeded_for_non_manager() {
      var task =
          Task.create(
              "TSK-0004",
              BOARD_ID,
              "Test task",
              TaskType.PLANNING,
              ModuleType.GENERAL,
              Priority.LOW,
              LocalDate.now().plusDays(10),
              null,
              null,
              null);
      task.startTodo();
      when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
      // WIP limit aşılmış (DEFAULT_WIP_LIMIT = 5)
      when(taskRepo.countInProgressByUser(USER_ID)).thenReturn(5L);

      assertThatThrownBy(
              () ->
                  taskService.updateStatus(
                      TASK_ID, new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS), USER_ID, false))
          .isInstanceOf(WipLimitExceededException.class)
          .hasMessageContaining("WIP limit exceeded");
    }

    @Test
    @DisplayName("Manager WIP limiti aşsa bile task alabilir")
    void should_allow_manager_even_when_wip_exceeded() {
      var task =
          Task.create(
              "TSK-0005",
              BOARD_ID,
              "Manager bypass test",
              TaskType.PLANNING,
              ModuleType.GENERAL,
              Priority.HIGH,
              LocalDate.now().plusDays(2),
              null,
              null,
              null);
      task.startTodo();
      when(taskRepo.findById(TASK_ID)).thenReturn(Optional.of(task));
      when(taskRepo.countInProgressByUser(USER_ID)).thenReturn(10L); // çok yüksek WIP
      when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // isManager = true olduğu için exception fırlatılmaz
      assertThatCode(
              () ->
                  taskService.updateStatus(
                      TASK_ID, new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS), USER_ID, true))
          .doesNotThrowAnyException();

      assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }
  }

  // =========================================================================
  // TASK OLUŞTURMA TESTLERİ
  // =========================================================================

  @Nested
  @DisplayName("createTask()")
  class CreateTaskTests {

    @Test
    @DisplayName("Yeni task BACKLOG status ile oluşturulur")
    void should_create_task_with_backlog_status() {
      var req =
          new CreateTaskRequest(
              BOARD_ID,
              "Yeni task",
              "Açıklama",
              TaskType.PRODUCTION,
              ModuleType.FIBER,
              Priority.HIGH,
              LocalDate.now().plusDays(5),
              new BigDecimal("8.0"),
              null,
              null,
              "MANUAL",
              null);

      // Task.getId() == null before JPA persist → save() returns a spy with id set
      when(taskRepo.save(any()))
          .thenAnswer(
              inv -> {
                Task t = inv.getArgument(0);
                // inject ID via reflection to simulate JPA persist
                var idField =
                    com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class
                        .getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(t, UUID.randomUUID());
                return t;
              });

      Task result = taskService.createTask(req);

      assertThat(result.getStatus()).isEqualTo(TaskStatus.BACKLOG);
      assertThat(result.getTitle()).isEqualTo("Yeni task");
      assertThat(result.getTaskType()).isEqualTo(TaskType.PRODUCTION);
      verify(taskRepo).save(any(Task.class));
    }

    @Test
    @DisplayName("PriorityScore createTask sırasında hesaplanır")
    void should_calculate_priority_score_on_creation() throws Exception {
      when(scoreCalculator.calculateWithLabels(any(), any())).thenReturn(75);
      var req =
          new CreateTaskRequest(
              BOARD_ID,
              "QC task",
              null,
              TaskType.QUALITY,
              ModuleType.FABRIC,
              Priority.CRITICAL,
              LocalDate.now().plusDays(1),
              null,
              null,
              null,
              "MANUAL",
              null);

      when(taskRepo.save(any()))
          .thenAnswer(
              inv -> {
                Task t = inv.getArgument(0);
                var idField =
                    com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class
                        .getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(t, UUID.randomUUID());
                return t;
              });

      Task result = taskService.createTask(req);

      assertThat(result.getPriorityScore()).isEqualTo(75);
    }
  }
}
