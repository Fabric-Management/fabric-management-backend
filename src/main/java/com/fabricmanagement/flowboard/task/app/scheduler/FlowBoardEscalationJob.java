package com.fabricmanagement.flowboard.task.app.scheduler;

import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository.BoardManagerProjection;
import com.fabricmanagement.flowboard.task.app.EscalationService;
import com.fabricmanagement.flowboard.task.domain.EscalationType;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.domain.SystemUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Açık görevlerin deadline, blocks ve unassigned sürelerini kontrol edip ilgili EscalationLog
 * kayıtlarını oluşturan scheduler.
 *
 * <p>[K3 FIX] Tenant-aware sorgular + [O2 FIX] try-catch + sayaç + [O9 FIX] Paginated sorgular
 *
 * <p>[O4 FIX] escalateToUserId artık Board.managerUserId üzerinden dinamik resolve edilir. Board'da
 * yönetici yoksa SystemUser.ID fallback olarak kullanılır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowBoardEscalationJob {

  private static final int PAGE_SIZE = 100;

  private final TaskRepository taskRepo;
  private final EscalationService escalationService;
  private final Clock clock;
  private final TenantService tenantService;
  private final BoardRepository boardRepository;

  @Scheduled(fixedRateString = "PT15M")
  public void runEscalationChecks() {
    log.info("Starting FlowBoardEscalationJob...");
    LocalDate today = LocalDate.now(clock);
    Instant blockedThreshold = Instant.now(clock).minus(2, ChronoUnit.DAYS);

    try {
      List<TenantDto> activeTenants = tenantService.getAllActive();

      for (TenantDto tenant : activeTenants) {
        UUID tenantId = tenant.getId();
        log.debug("Processing escalations for tenant: {}", tenantId);

        try {
          processDeadlinePassed(tenantId, today);
        } catch (Exception e) {
          log.error(
              "Failed DEADLINE_PASSED escalation for tenant {}: {}", tenantId, e.getMessage(), e);
        }

        try {
          processBlockedTooLong(tenantId, blockedThreshold);
        } catch (Exception e) {
          log.error(
              "Failed BLOCKED_TOO_LONG escalation for tenant {}: {}", tenantId, e.getMessage(), e);
        }
      }
    } catch (Exception e) {
      log.error("FlowBoardEscalationJob: Critical failure: {}", e.getMessage(), e);
    }

    log.info("FlowBoardEscalationJob completed.");
  }

  private void processDeadlinePassed(UUID tenantId, LocalDate today) {
    int escalated = 0;
    int failed = 0;
    Pageable pageable = PageRequest.of(0, PAGE_SIZE);
    Page<Task> page;

    do {
      page = taskRepo.findOpenTasksPastDeadline(tenantId, today, pageable);

      // [O4 FIX / N+1] Sayfadaki tüm board yöneticilerini tek sorguda çek
      Map<UUID, UUID> boardToManager = resolveBoardManagers(page.getContent());

      for (Task task : page.getContent()) {
        try {
          UUID notifyUserId = boardToManager.getOrDefault(task.getBoardId(), SystemUser.ID);

          boolean sent =
              escalationService.escalate(
                  task.getTenantId(),
                  task.getId(),
                  task.getTaskNumber(),
                  EscalationType.DEADLINE_PASSED,
                  notifyUserId,
                  "Görev teslim tarihini geçti: " + task.getDeadline(),
                  24);
          if (sent) escalated++;
        } catch (Exception e) {
          failed++;
          log.error("Failed to escalate deadline task {}: {}", task.getId(), e.getMessage());
        }
      }
      pageable = page.nextPageable();
    } while (page.hasNext());

    log.info("FlowBoardEscalationJob DEADLINE_PASSED: escalated={}, failed={}", escalated, failed);
  }

  private void processBlockedTooLong(UUID tenantId, Instant blockedThreshold) {
    int escalated = 0;
    int failed = 0;
    Pageable pageable = PageRequest.of(0, PAGE_SIZE);
    Page<Task> page;

    do {
      page = taskRepo.findBlockedTasksOlderThan(tenantId, blockedThreshold, pageable);

      // [O4 FIX / N+1] Sayfadaki tüm board yöneticilerini tek sorguda çek
      Map<UUID, UUID> boardToManager = resolveBoardManagers(page.getContent());

      for (Task task : page.getContent()) {
        try {
          UUID notifyUserId = boardToManager.getOrDefault(task.getBoardId(), SystemUser.ID);

          boolean sent =
              escalationService.escalate(
                  task.getTenantId(),
                  task.getId(),
                  task.getTaskNumber(),
                  EscalationType.BLOCKED_TOO_LONG,
                  notifyUserId,
                  "Görev uzun süredir bloklu durumda (> 2 gün).",
                  24);
          if (sent) escalated++;
        } catch (Exception e) {
          failed++;
          log.error("Failed to escalate blocked task {}: {}", task.getId(), e.getMessage());
        }
      }
      pageable = page.nextPageable();
    } while (page.hasNext());

    log.info("FlowBoardEscalationJob BLOCKED_TOO_LONG: escalated={}, failed={}", escalated, failed);
  }

  /**
   * Verilen task listesindeki tüm boardId'ler için tek DB sorgusunda boardId → managerUserId
   * haritası döner. N+1 önleme yöntemi.
   */
  private Map<UUID, UUID> resolveBoardManagers(List<Task> tasks) {
    Set<UUID> boardIds = tasks.stream().map(Task::getBoardId).collect(Collectors.toSet());

    return boardRepository.findManagerUserIdsByBoardIds(boardIds).stream()
        .filter(p -> p.getManagerUserId() != null)
        .collect(
            Collectors.toMap(
                BoardManagerProjection::getBoardId, BoardManagerProjection::getManagerUserId));
  }
}
