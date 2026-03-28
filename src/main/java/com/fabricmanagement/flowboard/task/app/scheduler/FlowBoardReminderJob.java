package com.fabricmanagement.flowboard.task.app.scheduler;

import com.fabricmanagement.flowboard.automation.domain.port.out.AutomationNotificationPort;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskReminder;
import com.fabricmanagement.flowboard.task.infra.repository.TaskReminderRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hatırlatma kayıtlarını periyodik olarak kontrol edip süresi gelenleri bildirime dönüştüren
 * scheduler.
 *
 * <p>[O8 FIX] boardId TaskRepository'den batch fetch ile çözümleniyor.
 *
 * <p>[D4 FIX] Kullanıcıya özel bildirim — notifyUser ile yönlendirme.
 *
 * <p>[O9 FIX] Paginated sorgu — büyük birikmiş reminder listelerinde OOM koruması.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowBoardReminderJob {

  private static final int PAGE_SIZE = 100;

  private final TaskReminderRepository reminderRepo;
  private final TaskRepository taskRepo;
  private final AutomationNotificationPort notificationPort;
  private final Clock clock;

  @Scheduled(fixedRateString = "PT5M")
  @Transactional
  public void runReminderChecks() {
    log.info("Starting FlowBoardReminderJob...");
    OffsetDateTime now = OffsetDateTime.now(clock);

    int totalSent = 0;
    int totalFailed = 0;
    Pageable pageable = PageRequest.of(0, PAGE_SIZE);
    Page<TaskReminder> page;

    do {
      page = reminderRepo.findByIsSentFalseAndTriggerAtBefore(now, pageable);

      if (page.isEmpty()) {
        break;
      }

      // [O8 FIX] Batch fetch — tüm taskId'leri topla, tek sorguda boardId'leri çek
      Set<UUID> taskIds =
          page.getContent().stream().map(TaskReminder::getTaskId).collect(Collectors.toSet());

      Map<UUID, Task> taskMap =
          taskRepo.findAllById(taskIds).stream()
              .collect(Collectors.toMap(Task::getId, Function.identity()));

      for (TaskReminder reminder : page.getContent()) {
        try {
          Task task = taskMap.get(reminder.getTaskId());
          UUID boardId = null;

          if (task != null) {
            boardId = task.getBoardId();
          } else {
            log.warn(
                "Reminder {} refers to non-existent task {} — sending with boardId=null",
                reminder.getId(),
                reminder.getTaskId());
          }

          // [D4 FIX] Kullanıcıya özel bildirim
          notificationPort.notifyUser(
              reminder.getTenantId(),
              boardId,
              reminder.getUserId(),
              reminder.getMessage(),
              reminder.getTaskId());

          reminder.markAsSent(clock);
          reminderRepo.save(reminder);
          totalSent++;
        } catch (Exception e) {
          totalFailed++;
          log.error("Failed to process reminder {}: {}", reminder.getId(), e.getMessage(), e);
        }
      }

      pageable = page.nextPageable();
    } while (page.hasNext());

    log.info("FlowBoardReminderJob completed: sent={}, failed={}", totalSent, totalFailed);
  }
}
