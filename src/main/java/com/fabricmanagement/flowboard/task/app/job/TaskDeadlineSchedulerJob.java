package com.fabricmanagement.flowboard.task.app.job;

import com.fabricmanagement.flowboard.automation.app.AutomationEngine;
import com.fabricmanagement.flowboard.automation.domain.AutomationContext;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Phase 3.1: Executes DEADLINE_APPROACHING automation rules for tasks nearing their deadline. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskDeadlineSchedulerJob {

  private final TaskRepository taskRepository;
  private final AutomationEngine automationEngine;

  /**
   * Runs hourly. Identifies open tasks whose deadline is within the next 24 hours and triggers the
   * automation engine.
   */
  @Scheduled(cron = "0 0 * * * *")
  @Transactional
  public void checkApproachingDeadlines() {
    log.debug("Starting TaskDeadlineSchedulerJob to check for approaching deadlines.");

    // Evaluate tasks whose deadline is within the next 24 hours (i.e. <= tomorrow)
    LocalDate thresholdDate = LocalDate.now().plusDays(1);

    List<Task> tasks = taskRepository.findTasksApproachingDeadline(thresholdDate);
    if (tasks.isEmpty()) {
      return;
    }

    log.info(
        "Found {} tasks approaching deadline. Triggering DEADLINE_APPROACHING automation rules.",
        tasks.size());

    for (Task task : tasks) {
      try {
        automationEngine.evaluate(
            task,
            AutomationTriggerType.DEADLINE_APPROACHING,
            AutomationContext.initial(task.getId(), task.getBoardId()));

        // NOT: Riski kabul ediyoruz: Eğer evaluate başarılı olup, save aşamasında
        // veritabanı Transaction'u patlarsa uyarı rule'u (örn: email dispatch) bir sonraki döngüde
        // iki kez gidebilir.
        // Rule'lar idempotent olmadığı için bu düşük ihtimali pragmatik olarak kabul ediyoruz.
        task.markDeadlineWarningFired();
        taskRepository.save(task);
      } catch (Exception e) {
        log.error(
            "Failed to process deadline warning for task {}: {}", task.getId(), e.getMessage());
      }
    }
  }
}
