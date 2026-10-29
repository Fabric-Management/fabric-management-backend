package com.fabricmanagement.flowboard.generator.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.generator.domain.port.out.SalesStockPlanningQueryPort;
import com.fabricmanagement.flowboard.task.app.TaskGenerationKey;
import com.fabricmanagement.flowboard.task.app.TaskProvisioningService;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesStockPlanningTaskService {
  private final SalesStockPlanningQueryPort source;
  private final BoardRepository boards;
  private final TaskRepository tasks;
  private final TaskProvisioningService provisioning;
  private final TaskService commands;
  private final jakarta.persistence.EntityManager entityManager;

  @Transactional
  public boolean synchronizeIfFabric(UUID orderId) {
    if (source.current(orderId, false).requirements().isEmpty()) return false;
    synchronize(orderId, false);
    return true;
  }

  @Transactional
  public void synchronize(UUID document, boolean quote) {
    var logical = source.logicalId(document, quote);
    // Serialise task reconciliation before reading the commercial root; not a stock lease.
    entityManager
        .createNativeQuery(
            "select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) held")
        .setParameter("key", TenantContext.requireTenantId() + ":SALES_STOCK_PLANNING:" + logical)
        .getSingleResult();
    var work = source.current(document, quote);
    String key =
        TaskGenerationKey.subject(
            "SALES_STOCK_PLANNING", work.logicalId(), TaskType.PLANNING, null);
    var existing =
        tasks.findByTenantIdAndGenerationKeyAndIsActiveTrueAndClosedAtIsNull(
            TenantContext.requireTenantId(), key);
    if (!work.active()) {
      existing.ifPresent(task -> commands.cancelTask(task.getId()));
      return;
    }
    if (existing.isEmpty()) {
      // Reuse already-open legacy generated planning work; do not overwrite its notes or identity.
      var legacy =
          tasks.findByTenantIdAndEntityTypeAndEntityIdAndTaskTypeAndIsActiveTrueAndClosedAtIsNull(
              TenantContext.requireTenantId(),
              work.entityType(),
              work.entityId(),
              TaskType.PLANNING);
      if (legacy.filter(task -> "TEMPLATE".equals(task.getSourceType())).isPresent()) return;
    }
    var board =
        boards
            .findByTenantIdAndBoardType(TenantContext.requireTenantId(), BoardType.FABRIC)
            .or(
                () ->
                    boards.findByTenantIdAndBoardType(
                        TenantContext.requireTenantId(), BoardType.GLOBAL));
    if (board.isEmpty()) {
      // As the template router does: a tenant without a board gets no task. Throwing here would
      // leave the event undelivered and retried forever (a fresh playground has no boards).
      log.warn("No FABRIC or GLOBAL board; sales stock planning task not created for {}", key);
      return;
    }
    String description = String.join("\n", work.requirements());
    var task =
        provisioning.createOrSynchronizeActive(
            new TaskCreation(
                board.get().getId(),
                work.reference(),
                description,
                TaskType.PLANNING,
                ModuleType.FABRIC,
                Priority.MEDIUM,
                null,
                null,
                work.entityType(),
                work.entityId(),
                "SALES_STOCK_PLANNING",
                work.logicalId(),
                key,
                Set.of(new TaskSubject(work.entityType(), work.entityId()))));
    // The logical work survives quote conversion; its target and current facts follow the order.
    task.synchronizeSalesStockSubject(
        work.entityType(), work.entityId(), work.reference(), description);
    tasks.save(task);
  }
}
