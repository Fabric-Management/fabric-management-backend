package com.fabricmanagement.flowboard.generator.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.flowboard.board.domain.Board;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.generator.domain.TaskTemplate;
import com.fabricmanagement.flowboard.generator.infra.repository.TaskTemplateRepository;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.dto.CreateTaskRequest;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmartTaskGeneratorListener")
class SmartTaskGeneratorListenerTest {

  @Mock private TaskTemplateRepository templateRepo;
  @Mock private TaskService taskService;
  @Mock private TaskRepository taskRepo;
  @Mock private BoardRepository boardRepo;
  @Mock private StockControlEngine stockControlEngine;

  @InjectMocks private SmartTaskGeneratorListener listener;

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID SALES_ORDER_ID = UUID.randomUUID();
  private static final UUID BOARD_ID = UUID.randomUUID();

  private SalesOrderConfirmedEvent salesOrderEvent;

  @BeforeEach
  void setUp() {
    salesOrderEvent =
        new SalesOrderConfirmedEvent(
            TENANT_ID,
            SALES_ORDER_ID,
            "SO-001",
            UUID.randomUUID(),
            "Test Customer",
            BigDecimal.TEN,
            "KG",
            LocalDate.now().plusDays(30));
  }

  @Nested
  @DisplayName("SalesOrderConfirmed")
  class SalesOrderConfirmedTests {

    @Test
    @DisplayName("Template bulunamazsa task oluşturulmaz")
    void noTemplate_skipsTaskCreation() {
      when(templateRepo.findByEventTypeAndIsActiveTrue("SalesOrderConfirmed"))
          .thenReturn(List.of());

      listener.onSalesOrderConfirmed(salesOrderEvent);

      verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("Template bulunursa ve board varsa task oluşturur")
    void templateFound_boardFound_createsTask() {
      TaskTemplate template = mock(TaskTemplate.class);
      when(template.getTaskType()).thenReturn(TaskType.PRODUCTION);
      when(template.getTitleTemplate()).thenReturn("Üretim — {salesOrder.orderNumber}");
      when(template.getDefaultPriority()).thenReturn(Priority.HIGH);
      when(template.getModuleType()).thenReturn(null);
      when(template.getEstimatedHours()).thenReturn(BigDecimal.valueOf(8));
      when(template.getAutoLabels()).thenReturn(null);

      when(templateRepo.findByEventTypeAndIsActiveTrue("SalesOrderConfirmed"))
          .thenReturn(List.of(template));

      when(stockControlEngine.analyze(salesOrderEvent))
          .thenReturn(
              List.of(new StockControlEngine.StockDecision(TaskType.PRODUCTION, BigDecimal.TEN)));

      // [P1 FIX] findByTenantIdAndBoardType kullanılıyor
      Board board = mock(Board.class);
      when(board.getId()).thenReturn(BOARD_ID);
      when(boardRepo.findByTenantIdAndBoardType(TENANT_ID, BoardType.GLOBAL))
          .thenReturn(Optional.of(board));

      // [P2 FIX] existsOpenTaskByEntityAndType kullanılıyor
      when(taskRepo.existsOpenTaskByEntityAndType(
              eq("SALES_ORDER"), eq(SALES_ORDER_ID), eq(TaskType.PRODUCTION)))
          .thenReturn(false);

      Task createdTask = mock(Task.class);
      when(createdTask.getId()).thenReturn(UUID.randomUUID());
      when(taskService.createTask(any())).thenReturn(createdTask);

      listener.onSalesOrderConfirmed(salesOrderEvent);

      ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
      verify(taskService).createTask(captor.capture());
      assertThat(captor.getValue().title()).contains("SO-001");
      assertThat(captor.getValue().taskType()).isEqualTo(TaskType.PRODUCTION);
      assertThat(captor.getValue().boardId()).isEqualTo(BOARD_ID);
    }

    @Test
    @DisplayName("İdempotency: aynı entity için açık task varsa oluşturmaz")
    void idempotency_existingOpenTask_skipsCreation() {
      TaskTemplate template = mock(TaskTemplate.class);
      when(template.getTaskType()).thenReturn(TaskType.PRODUCTION);
      when(template.getTitleTemplate()).thenReturn("Üretim — {salesOrder.orderNumber}");
      when(template.getAutoLabels()).thenReturn(null);

      when(templateRepo.findByEventTypeAndIsActiveTrue("SalesOrderConfirmed"))
          .thenReturn(List.of(template));
      when(stockControlEngine.analyze(salesOrderEvent))
          .thenReturn(
              List.of(new StockControlEngine.StockDecision(TaskType.PRODUCTION, BigDecimal.TEN)));

      // [P2 FIX] existsOpenTaskByEntityAndType → true: açık task var
      when(taskRepo.existsOpenTaskByEntityAndType(
              "SALES_ORDER", SALES_ORDER_ID, TaskType.PRODUCTION))
          .thenReturn(true);

      listener.onSalesOrderConfirmed(salesOrderEvent);

      verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("Board bulunamazsa task oluşturulmaz")
    void noBoard_skipsTaskCreation() {
      TaskTemplate template = mock(TaskTemplate.class);
      when(template.getTaskType()).thenReturn(TaskType.PRODUCTION);
      when(template.getTitleTemplate()).thenReturn("Üretim — {salesOrder.orderNumber}");
      when(template.getModuleType()).thenReturn(null);
      when(template.getAutoLabels()).thenReturn(null);
      when(template.getId()).thenReturn(UUID.randomUUID());

      when(templateRepo.findByEventTypeAndIsActiveTrue("SalesOrderConfirmed"))
          .thenReturn(List.of(template));
      when(stockControlEngine.analyze(salesOrderEvent))
          .thenReturn(
              List.of(new StockControlEngine.StockDecision(TaskType.PRODUCTION, BigDecimal.TEN)));

      // Board bulunamıyor
      when(taskRepo.existsOpenTaskByEntityAndType(any(), any(), any())).thenReturn(false);
      when(boardRepo.findByTenantIdAndBoardType(any(), any())).thenReturn(Optional.empty());

      listener.onSalesOrderConfirmed(salesOrderEvent);

      verifyNoInteractions(taskService);
    }
  }
}
