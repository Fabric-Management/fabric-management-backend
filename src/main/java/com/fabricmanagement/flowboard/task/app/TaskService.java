package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.domain.event.TaskAssignedEvent;
import com.fabricmanagement.flowboard.task.domain.event.TaskCreatedEvent;
import com.fabricmanagement.flowboard.task.domain.event.TaskStatusChangedEvent;
import com.fabricmanagement.flowboard.task.dto.*;
import com.fabricmanagement.flowboard.task.infra.repository.*;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.domain.SystemUser;
import jakarta.persistence.EntityNotFoundException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FlowBoard görev yönetimi servisi.
 *
 * <p>Sorumluluklar:
 *
 * <ul>
 *   <li>Task CRUD
 *   <li>Status geçiş validasyonu + WIP limiti kontrolü
 *   <li>PriorityScore hesaplama
 *   <li>TaskAssignee yönetimi
 *   <li>WebSocket yayını
 * </ul>
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — Task Status Akışı, WIP Limiti
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

  /** Varsayılan kullanıcı WIP limiti — User entity'sinde override edilebilir. */
  private static final int DEFAULT_WIP_LIMIT = 5;

  private final TaskRepository taskRepo;
  private final TaskAssigneeRepository assigneeRepo;
  private final BoardRepository boardRepo;
  private final PriorityScoreCalculator scoreCalculator;
  private final DomainEventPublisher eventPublisher;
  private final UserFacade userFacade;

  // =========================================================================
  // TASK OLUŞTURMA
  // =========================================================================

  /**
   * Manuel olarak yeni task oluşturur.
   *
   * <p>SmartTaskGenerator da bu metodu kullanır.
   */
  @Transactional
  public Task createTask(CreateTaskRequest req) {
    // Board varlık kontrolü
    boardRepo
        .findById(req.boardId())
        .orElseThrow(() -> new EntityNotFoundException("Board not found: " + req.boardId()));

    // [L2 FIX] Task numarası üret — DB sequence ile race-condition safe
    String taskNumber = generateTaskNumber();

    Task task =
        Task.create(
            taskNumber,
            req.boardId(),
            req.title(),
            req.taskType(),
            req.moduleType(),
            req.priority(), // [Q2 FIX] double default kaldırıldı, Task.create() handler
            req.deadline(),
            req.estimatedHours(),
            req.entityType(),
            req.entityId());

    // [X3 FIX] description set edilmiyordu
    if (req.description() != null) {
      task.updateDescription(req.description());
    }

    // PriorityScore hesapla
    int score = scoreCalculator.calculateWithLabels(task, List.of());
    task.updatePriorityScore(score);

    Task saved = taskRepo.save(task);

    log.info(
        "Task created: taskNumber={} boardId={} taskType={}",
        taskNumber,
        req.boardId(),
        req.taskType());

    // [B2 FIX] Domain event yayınla — WS publish TaskEventListener (AFTER_COMMIT) tarafından
    // yapılacak.
    // [EV1 FIX] TaskCreatedEvent artık yayınlanıyor.
    eventPublisher.publish(
        new TaskCreatedEvent(
            TenantContext.getCurrentTenantId(),
            saved.getId(),
            req.boardId(),
            taskNumber,
            TaskStatus.BACKLOG.name(),
            req.taskType().name()));

    return saved;
  }

  // =========================================================================
  // STATUS GEÇİŞİ
  // =========================================================================

  /**
   * Task status'ünü günceller — validasyon ve WIP kontrolü ile.
   *
   * @param taskId Güncellenecek task
   * @param req Yeni status ve atama bilgileri
   * @param requestingUserId İşlemi yapan kullanıcı (WIP kontrolü için)
   * @param isManager Manager mı? (WIP bypass yetkisi)
   * @return Güncellenmiş task
   * @throws WipLimitExceededException SELF assign + WIP aşıldığında
   */
  @Transactional
  public Task updateStatus(
      UUID taskId, UpdateTaskStatusRequest req, UUID requestingUserId, boolean isManager) {

    Task task =
        taskRepo
            .findById(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

    TaskStatus targetStatus = req.newStatus();

    // [L1 FIX] oldStatus'ü geçiş ÖNCESİNDE kaydet
    TaskStatus oldStatus = task.getStatus();

    // IN_PROGRESS'e geçerken WIP kontrolü
    if (targetStatus == TaskStatus.IN_PROGRESS) {
      long currentWip = taskRepo.countInProgressByUser(requestingUserId);
      int wipLimit =
          userFacade
              .findById(TenantContext.getCurrentTenantId(), requestingUserId)
              .map(dto -> dto.getWipLimit() != null ? dto.getWipLimit() : DEFAULT_WIP_LIMIT)
              .orElse(DEFAULT_WIP_LIMIT);

      if (currentWip >= wipLimit) {
        if (!isManager) {
          throw new WipLimitExceededException(
              String.format(
                  "WIP limit exceeded for user %s. Current: %d, Limit: %d",
                  requestingUserId, currentWip, wipLimit));
        }
        log.warn(
            "Manager bypassing WIP limit for user={} currentWip={} limit={}",
            requestingUserId,
            currentWip,
            wipLimit);
      }
    }

    // Status geçişini uygula
    applyStatusTransition(task, targetStatus);

    // [P3 FIX] Score sadece deadline'a yaklaştığında değişir, status geçişi değiştirmez
    // Ama DONE/CANCELLED'a geçerken skor 0'a düşürülmeli
    if (targetStatus == TaskStatus.DONE || targetStatus == TaskStatus.CANCELLED) {
      task.updatePriorityScore(0);
    }

    Task saved = taskRepo.save(task);

    // [B2+EV1 FIX] Domain event yayınla — WS publish AFTER_COMMIT'te yapılacak
    eventPublisher.publish(
        new TaskStatusChangedEvent(
            TenantContext.getCurrentTenantId(),
            taskId,
            task.getBoardId(),
            oldStatus.name(),
            targetStatus.name(),
            requestingUserId));

    log.info("Task status changed: taskId={} {} → {}", taskId, oldStatus, targetStatus);
    return saved;
  }

  // =========================================================================
  // SORGULAR
  // =========================================================================

  /** Board'daki tüm task'ları priorityScore DESC sırasında döner. */
  @Transactional(readOnly = true)
  public List<Task> getTasksByBoard(UUID boardId) {
    return taskRepo.findAllByBoardIdAndIsActiveTrueOrderByPriorityScoreDesc(boardId);
  }

  /** [P2 FIX] Board'daki task'ları sayfalı getirir — büyük board'lar için. */
  @Transactional(readOnly = true)
  public Page<Task> getTasksByBoard(UUID boardId, Pageable pageable) {
    return taskRepo.findAllByBoardIdAndIsActiveTrue(boardId, pageable);
  }

  /**
   * [F3 FIX] Kanban view — tüm status'ler dahil (boş olanlar bile).
   *
   * @return Her TaskStatus için task listesi (boş status'ler boş liste ile döner)
   */
  @Transactional(readOnly = true)
  public Map<TaskStatus, List<Task>> getKanbanView(UUID boardId) {
    // Tüm status'leri başlat — boş board'da bile tüm kolonlar görünsün
    Map<TaskStatus, List<Task>> kanban = new LinkedHashMap<>();
    for (TaskStatus status : TaskStatus.values()) {
      kanban.put(status, new ArrayList<>());
    }
    // Task'ları ilgili kolona dağıt
    List<Task> tasks = getTasksByBoard(boardId);
    for (Task task : tasks) {
      kanban.get(task.getStatus()).add(task);
    }
    return kanban;
  }

  /** Task detayını getirir. */
  @Transactional(readOnly = true)
  public Task getTask(UUID taskId) {
    return taskRepo
        .findById(taskId)
        .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
  }

  // =========================================================================
  // ATAMA
  // =========================================================================

  /**
   * Task'ı bir kullanıcıya atar.
   *
   * @param taskId Atanacak task
   * @param userId Atanacak kullanıcı
   * @param assignedBy Atama yapan (SYSTEM/MANAGER/SELF)
   * @param requestedByUserId Atama işlemini gerçekleştiren kullanıcı
   * @throws IllegalStateException aynı kullanıcı zaten atanmışsa
   */
  @Transactional
  public void assignToUser(
      UUID taskId, UUID userId, AssignedBy assignedBy, UUID requestedByUserId) {
    // [L3 FIX] Tek sorgu — task + boardId birlikte
    if (!taskRepo.existsById(taskId)) {
      throw new EntityNotFoundException("Task not found: " + taskId);
    }

    // [L4 FIX] Duplicate assignment koruması
    if (assigneeRepo.findByTaskIdAndUserIdAndIsActiveTrue(taskId, userId).isPresent()) {
      throw new IllegalStateException("User " + userId + " is already assigned to task " + taskId);
    }

    var assignee = TaskAssignee.assignToUser(taskId, userId, assignedBy);
    assigneeRepo.save(assignee);

    // [AUT5 FIX] Task atama bildirimi — Domain Event yayınlanıyor
    // [O1 FIX] assignedByUserId artık dışarıdan alınıyor — kim atadığı bilgisi korunuyor.
    UUID assignedByUserId = (assignedBy == AssignedBy.SYSTEM) ? SystemUser.ID : requestedByUserId;
    eventPublisher.publish(
        new TaskAssignedEvent(
            TenantContext.getCurrentTenantId(), taskId, userId, assignedByUserId));

    log.info("Task assigned: taskId={} userId={} assignedBy={}", taskId, userId, assignedBy);
  }

  // =========================================================================
  // İPTAL
  // =========================================================================

  /**
   * [F1 FIX] Task'ı iptal eder — WS bildirimi ile.
   *
   * <p>[F2 FIX] cancelTask ve updateStatus(CANCELLED) aynı davranışı verir.
   */
  @Transactional
  public void cancelTask(UUID taskId) {
    Task task =
        taskRepo
            .findById(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

    TaskStatus oldStatus = task.getStatus();
    task.cancel();
    task.updatePriorityScore(0);
    taskRepo.save(task);

    // Domain event yayınla — TaskStatusChangedEvent üzerinden WS yayını AFTER_COMMIT'te yapılır
    eventPublisher.publish(
        new TaskStatusChangedEvent(
            TenantContext.getCurrentTenantId(),
            taskId,
            task.getBoardId(),
            oldStatus.name(),
            TaskStatus.CANCELLED.name(),
            null));

    log.info("Task cancelled: taskId={} oldStatus={}", taskId, oldStatus);
  }

  // =========================================================================
  // PRIVATE
  // =========================================================================

  private void applyStatusTransition(Task task, TaskStatus target) {
    switch (target) {
      case TODO -> task.startTodo();
      case IN_PROGRESS -> task.startProgress();
      case IN_REVIEW -> task.submitForReview();
      case DONE -> task.markDone();
      case BLOCKED -> task.block();
      case CANCELLED -> task.cancel();
      default -> throw new IllegalArgumentException("Unsupported status transition to: " + target);
    }
  }

  /**
   * [L2 FIX] DB sequence ile race-condition safe task numarası üretir. Migration'da tanımlı:
   * flowboard.task_number_seq
   */
  private String generateTaskNumber() {
    long nextVal = taskRepo.getNextTaskNumber();
    return String.format("TSK-%04d", nextVal);
  }
}
