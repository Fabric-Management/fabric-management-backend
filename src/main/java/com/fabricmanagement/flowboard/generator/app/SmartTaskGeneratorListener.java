package com.fabricmanagement.flowboard.generator.app;

import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.generator.domain.TaskTemplate;
import com.fabricmanagement.flowboard.generator.infra.repository.TaskTemplateRepository;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.dto.CreateTaskRequest;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Domain event dinleyerek otomatik Task oluşturur.
 *
 * <p>Akış:
 *
 * <ol>
 *   <li>Event alınır → eventType string'e çevrilir
 *   <li>TaskTemplate bulunur ({@link TaskTemplateRepository#findByEventTypeAndIsActiveTrue})
 *   <li>titleTemplate değişkenleri interpolate edilir
 *   <li>SalesOrderConfirmed ise → {@link StockControlEngine#analyze()} çağrılır
 *   <li>{@link TaskService#createTask()} ile task oluşturulur — idempotency korumalı
 * </ol>
 *
 * <p>[F1 FIX] {@code @TransactionalEventListener(AFTER_COMMIT)} ile source event commit edilmeden
 * task oluşturulması önlenir.
 *
 * <p>Docs: {@code 07-flowboard/smart-task-generator.md} — Bölüm 2. Event → Task Zinciri
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartTaskGeneratorListener {

  private final TaskTemplateRepository templateRepo;
  private final TaskService taskService;
  private final TaskRepository taskRepo;
  private final BoardRepository boardRepo;
  private final StockControlEngine stockControlEngine;

  // =========================================================================
  // SALES ORDER
  // =========================================================================

  /**
   * [F1 FIX] AFTER_COMMIT — source event transaction commit olduktan sonra çalışır.
   *
   * <p>SalesOrderConfirmed → StockControlEngine analizi + template'den task oluşturma.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  @Transactional
  public void onSalesOrderConfirmed(SalesOrderConfirmedEvent event) {
    List<TaskTemplate> templates =
        templateRepo.findByEventTypeAndIsActiveTrue("SalesOrderConfirmed");
    if (templates.isEmpty()) {
      log.debug("No active TaskTemplate for SalesOrderConfirmed — skipping");
      return;
    }

    // Stok analizi — hangi task türü oluşturulacak?
    var stockDecisions = stockControlEngine.analyze(event);

    for (var decision : stockDecisions) {
      var matchingTemplates =
          templates.stream().filter(t -> t.getTaskType() == decision.taskType()).toList();
      for (TaskTemplate template : matchingTemplates) {
        String title = interpolateTitle(template.getTitleTemplate(), event);
        createTaskFromTemplate(
            template,
            title,
            decision.taskType(),
            event.getRequestedDeliveryDate(),
            "SALES_ORDER",
            event.getSalesOrderId(),
            event.getTenantId());
      }
    }
  }

  // =========================================================================
  // WORK ORDER
  // =========================================================================

  /**
   * [F1 FIX] AFTER_COMMIT — WorkOrderApproved event commit sonrası.
   *
   * <p>[L1 FIX] Template'den titleTemplate kullanılıyor, hardcoded title yerine.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  @Transactional
  public void onWorkOrderApproved(WorkOrderApprovedEvent event) {
    processTemplateEvent(
        "WorkOrderApproved",
        event.getWorkOrderNumber(),
        null,
        "WORK_ORDER",
        event.getWorkOrderId(),
        event.getTenantId());
  }

  // =========================================================================
  // GOODS RECEIPT
  // =========================================================================

  /**
   * [F1 FIX] AFTER_COMMIT — GoodsReceiptConfirmed event commit sonrası.
   *
   * <p>[L2 FIX] Template'den titleTemplate kullanılıyor, hardcoded title yerine.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  @Transactional
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    processTemplateEvent(
        "GoodsReceiptConfirmed",
        event.receiptNumber(),
        null,
        "GOODS_RECEIPT",
        event.receiptId(),
        event.tenantId());
  }

  // =========================================================================
  // PRIVATE HELPERS
  // =========================================================================

  /**
   * Genel event → template eşleştirme: template varsa titleTemplate kullanır, yoksa entityRef ile
   * fallback title oluşturur.
   *
   * <p>[L1/L2 FIX] Artık template titleTemplate'i kullanılıyor — hardcoded title kaldırıldı.
   */
  private void processTemplateEvent(
      String eventType,
      String entityRef,
      LocalDate deadline,
      String entityType,
      UUID entityId,
      UUID tenantId) {
    List<TaskTemplate> templates = templateRepo.findByEventTypeAndIsActiveTrue(eventType);
    if (templates.isEmpty()) {
      log.debug("No active TaskTemplate for {} — skipping", eventType);
      return;
    }
    for (TaskTemplate template : templates) {
      // [L1/L2 FIX] Template titleTemplate kullan, entityRef ile basit interpolation
      String title =
          template.getTitleTemplate() != null
              ? template.getTitleTemplate().replace("{entityRef}", entityRef)
              : eventType + " — " + entityRef;
      createTaskFromTemplate(
          template, title, template.getTaskType(), deadline, entityType, entityId, tenantId);
    }
  }

  /**
   * Idempotency korumalı task oluşturma.
   *
   * <p>[P2 FIX] {@code existsOpenTaskByEntityAndType} ile belleğe yükleme yerine DB COUNT sorgusu.
   *
   * <p>[L3 FIX] entityId null ise uyarı loglar — idempotency çalışmaz.
   *
   * <p>[X3 FIX] Template auto_labels bilgisi loglanır (label atama Faz 8.3'te implement edilecek).
   */
  private void createTaskFromTemplate(
      TaskTemplate template,
      String title,
      TaskType taskType,
      LocalDate deadline,
      String entityType,
      UUID entityId,
      UUID tenantId) {

    // [L3 FIX] entityId null kontrolü — idempotency çalışmaz
    if (entityId == null) {
      log.warn(
          "SmartTaskGenerator: entityId is null for eventType={} — idempotency check skipped, "
              + "duplicate task risk exists",
          template.getEventType());
    }

    // [P2 FIX] Idempotency: DB sorgusu ile kontrol — belleğe yükleme yok
    if (entityId != null
        && taskRepo.existsOpenTaskByEntityAndType(entityType, entityId, taskType)) {
      log.info(
          "Idempotency: {} task already open for entity {}={} — skipping",
          taskType,
          entityType,
          entityId);
      return;
    }

    // [P1 FIX] Board bul: dedicated query ile ilk eşleşen board
    UUID boardId = resolveBoardId(template, tenantId);
    if (boardId == null) {
      log.warn("No matching board for template={} — task not created", template.getId());
      return;
    }

    // [X3] auto_labels bilgilendirme (atama Faz 8.3 TaskLabelService ile)
    if (template.getAutoLabels() != null && !template.getAutoLabels().isBlank()) {
      log.info(
          "SmartTaskGenerator: auto_labels={} for taskType={} — label assignment pending Faz 8.3",
          template.getAutoLabels(),
          taskType);
    }

    var req =
        new CreateTaskRequest(
            boardId,
            title,
            null,
            taskType,
            template.getModuleType() != null ? template.getModuleType() : ModuleType.GENERAL,
            template.getDefaultPriority(),
            deadline,
            template.getEstimatedHours(),
            entityType,
            entityId);

    var task = taskService.createTask(req);
    log.info(
        "SmartTaskGenerator created: taskId={} taskType={} entityType={}",
        task.getId(),
        taskType,
        entityType);
  }

  /**
   * [P1 FIX] Board lookup — dedicated repository query ile doğrudan bulur.
   *
   * <p>[Q4 FIX] BoardType enum karşılaştırması name() string yerine dedicated query ile yapılır.
   */
  private UUID resolveBoardId(TaskTemplate template, UUID tenantId) {
    if (template.getModuleType() != null) {
      // ModuleType → BoardType mapping: aynı isimlere sahip → valueOf ile dönüştür
      try {
        BoardType boardType = BoardType.valueOf(template.getModuleType().name());
        return boardRepo
            .findByTenantIdAndBoardType(tenantId, boardType)
            .map(b -> b.getId())
            .orElse(null);
      } catch (IllegalArgumentException e) {
        // ModuleType enum'da olan ama BoardType'da olmayan (ör. GENERAL → GLOBAL)
        log.debug(
            "No matching BoardType for moduleType={} — falling back to GLOBAL",
            template.getModuleType());
      }
    }
    // Fallback: GLOBAL board
    return boardRepo
        .findByTenantIdAndBoardType(tenantId, BoardType.GLOBAL)
        .map(b -> b.getId())
        .orElse(null);
  }

  /** SalesOrder event'i için title interpolation. */
  private String interpolateTitle(String titleTemplate, SalesOrderConfirmedEvent event) {
    if (titleTemplate == null) return "SalesOrder — " + event.getOrderNumber();
    return titleTemplate
        .replace("{salesOrder.orderNumber}", event.getOrderNumber())
        .replace(
            "{salesOrder.customerName}",
            event.getCustomerName() != null ? event.getCustomerName() : "");
  }
}
