package com.fabricmanagement.flowboard.task.app.scheduler;

import com.fabricmanagement.flowboard.task.app.RecurringTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowBoardRecurringJob {

  private final RecurringTaskService recurringTaskService;

  /** Saat başı çalışarak, zamanı gelmiş periyodik taskları spawn eder. */
  @Scheduled(cron = "0 0 * * * *") // Her saat başı
  public void runRecurringChecks() {
    log.info("Starting FlowBoardRecurringJob...");
    recurringTaskService.triggerDueTemplates();
    log.info("FlowBoardRecurringJob completed.");
  }
}
