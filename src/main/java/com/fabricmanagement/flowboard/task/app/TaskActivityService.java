package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.task.domain.TaskAction;
import com.fabricmanagement.flowboard.task.domain.TaskActivityLog;
import com.fabricmanagement.flowboard.task.infra.repository.TaskActivityLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskActivityService {

  private final TaskActivityLogRepository logRepository;

  /**
   * Görev üzerindeki işlemleri loglar. Kendi transaction sınırını kullanır (asenkron veya ana
   * işlemin başarısız olmasını engellemek için isteğe göre REQUIRE_NEW yapılabilir, ancak şu an
   * default MANDATORY / REQUIRED mantığında ana akışla devam edecektir. Biz REQUIRED bırakıyoruz).
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public void logActivity(
      UUID tenantId,
      UUID taskId,
      UUID userId,
      TaskAction action,
      String oldValue,
      String newValue,
      String metadata) {

    var activityLog =
        new TaskActivityLog(tenantId, taskId, userId, action, oldValue, newValue, metadata);
    logRepository.save(activityLog);

    log.debug("Task activity logged: taskId={}, action={}", taskId, action);
  }
}
