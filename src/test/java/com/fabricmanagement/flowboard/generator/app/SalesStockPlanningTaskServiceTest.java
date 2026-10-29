package com.fabricmanagement.flowboard.generator.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.board.domain.Board;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.generator.domain.port.out.SalesStockPlanningQueryPort;
import com.fabricmanagement.flowboard.task.app.*;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.*;
import org.junit.jupiter.api.*;

class SalesStockPlanningTaskServiceTest {
  final UUID tenant = UUID.randomUUID(), quote = UUID.randomUUID(), order = UUID.randomUUID();
  final SalesStockPlanningQueryPort source = mock(SalesStockPlanningQueryPort.class);
  final BoardRepository boards = mock(BoardRepository.class);
  final TaskRepository tasks = mock(TaskRepository.class);
  final TaskProvisioningService provisioning = mock(TaskProvisioningService.class);
  final TaskService commands = mock(TaskService.class);
  final EntityManager entityManager = mock(EntityManager.class);
  final SalesStockPlanningTaskService service =
      new SalesStockPlanningTaskService(
          source, boards, tasks, provisioning, commands, entityManager);

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    var query = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(source.logicalId(any(), anyBoolean())).thenReturn(quote);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void quoteConversionUsesTheSameLogicalPlanningIdentityAndTheCurrentOrderTarget() {
    var board = mock(Board.class);
    when(board.getId()).thenReturn(UUID.randomUUID());
    when(boards.findByTenantIdAndBoardType(tenant, BoardType.FABRIC))
        .thenReturn(Optional.of(board));
    when(source.current(quote, true))
        .thenReturn(
            new SalesStockPlanningQueryPort.Work(
                quote,
                "SALES_ORDER",
                order,
                "SO-2026-00125",
                true,
                List.of("2,050 M: choose source rolls and assess cutting")));
    var task = mock(Task.class);
    when(provisioning.createOrSynchronizeActive(any())).thenReturn(task);
    service.synchronize(quote, true);
    service.synchronize(quote, true);
    var command = org.mockito.ArgumentCaptor.forClass(TaskCreation.class);
    verify(provisioning, times(2)).createOrSynchronizeActive(command.capture());
    assertThat(command.getAllValues())
        .extracting(TaskCreation::generationKey)
        .containsOnly(
            TaskGenerationKey.subject("SALES_STOCK_PLANNING", quote, TaskType.PLANNING, null));
    assertThat(command.getValue().entityId()).isEqualTo(order);
    verify(task, times(2))
        .synchronizeSalesStockSubject(
            eq("SALES_ORDER"), eq(order), eq("SO-2026-00125"), anyString());
  }

  @Test
  void anOpenLegacyPlanningTaskIsReusedWithoutOverwritingItsNotes() {
    when(source.current(order, false))
        .thenReturn(
            new SalesStockPlanningQueryPort.Work(
                quote,
                "SALES_ORDER",
                order,
                "SO-2026-00125",
                true,
                List.of("Review the pre-reserved rolls")));
    var task = mock(Task.class);
    when(task.getSourceType()).thenReturn("TEMPLATE");
    when(tasks.findByTenantIdAndEntityTypeAndEntityIdAndTaskTypeAndIsActiveTrueAndClosedAtIsNull(
            tenant, "SALES_ORDER", order, TaskType.PLANNING))
        .thenReturn(Optional.of(task));
    service.synchronize(order, false);
    verifyNoInteractions(provisioning, boards, commands);
    verify(tasks, never()).save(any());
  }

  @Test
  void unrelatedModulesRetainTheirExistingTemplateRoute() {
    when(source.current(order, false))
        .thenReturn(
            new SalesStockPlanningQueryPort.Work(
                order, "SALES_ORDER", order, "SO-2026-00125", false, List.of()));
    assertThat(service.synchronizeIfFabric(order)).isFalse();
    verifyNoInteractions(entityManager, provisioning, boards, commands, tasks);
  }

  @Test
  void staleInvalidationAfterCancellationClosesTheExistingWorkInsteadOfReopeningIt() {
    when(source.current(order, false))
        .thenReturn(
            new SalesStockPlanningQueryPort.Work(
                quote, "SALES_ORDER", order, "SO-2026-00125", false, List.of()));
    var task = mock(Task.class);
    UUID taskId = UUID.randomUUID();
    when(task.getId()).thenReturn(taskId);
    when(tasks.findByTenantIdAndGenerationKeyAndIsActiveTrueAndClosedAtIsNull(
            eq(tenant), anyString()))
        .thenReturn(Optional.of(task));
    service.synchronize(order, false);
    verify(commands).cancelTask(taskId);
    verifyNoInteractions(provisioning, boards);
  }
}
