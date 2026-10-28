package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.flowboard.board.domain.*;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.generator.app.SalesStockPlanningTaskService;
import com.fabricmanagement.flowboard.generator.domain.port.out.SalesStockPlanningQueryPort;
import com.fabricmanagement.flowboard.task.app.*;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SalesStockPlanningHandoffIT extends MetreOrderITSupport {
  @Autowired SalesStockPlanningQueryPort source;
  @Autowired BoardRepository boards;
  @Autowired TaskRepository tasks;
  @Autowired TaskProvisioningService provisioning;
  @Autowired TaskService taskCommands;
  @Autowired EntityManager entityManager;

  @Test
  void metreOnlyWorkIsIdempotentAndFollowsTheQuoteIntoItsOrder() {
    var writer =
        new SalesStockPlanningTaskService(
            source, boards, tasks, provisioning, taskCommands, entityManager);
    UUID taskId =
        in(
            tenant,
            () -> {
              boards.saveAndFlush(
                  Board.create("Fabric planning", BoardType.FABRIC, 5, ViewType.KANBAN, null));
              writer.synchronize(quoteId, true);
              writer.synchronize(quoteId, true);
              return tasks
                  .findByTenantIdAndGenerationKeyAndIsActiveTrueAndClosedAtIsNull(tenant, key())
                  .orElseThrow()
                  .getId();
            });
    in(
        tenant,
        () -> {
          var quote = quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, quoteId).orElseThrow();
          quote.setStatus(QuoteStatus.APPROVED);
          quotes.saveAndFlush(quote);
          return null;
        });
    var request = metreOrder(null);
    request.setQuoteId(quoteId);
    request.getLines().getFirst().setSourceQuoteLineId(quoteLineId);
    var order = in(tenant, () -> orders.createOrder(request));
    in(
        tenant,
        () -> {
          // A delayed quote invalidation must now reconcile the current order, not reopen quote
          // work.
          writer.synchronize(quoteId, true);
          writer.synchronize(order.getId(), false);
          var current =
              tasks
                  .findByTenantIdAndGenerationKeyAndIsActiveTrueAndClosedAtIsNull(tenant, key())
                  .orElseThrow();
          assertThat(current.getId()).isEqualTo(taskId);
          assertThat(current.getEntityId()).isEqualTo(order.getId());
          assertThat(current.getEntityType()).isEqualTo("SALES_ORDER");
          return null;
        });
  }

  private String key() {
    return TaskGenerationKey.subject("SALES_STOCK_PLANNING", quoteId, TaskType.PLANNING, null);
  }
}
