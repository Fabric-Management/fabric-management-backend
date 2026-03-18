package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskReminder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskReminderRepository extends JpaRepository<TaskReminder, UUID> {

  List<TaskReminder> findAllByTaskIdOrderByTriggerAtAsc(UUID taskId);

  // Hatırlatma zamanı gelmiş ama henüz gönderilmemiş olanları bulur
  List<TaskReminder> findByIsSentFalseAndTriggerAtBefore(OffsetDateTime now);

  // [O9 FIX] Paginated versiyon — scheduler batch işlemleri için
  Page<TaskReminder> findByIsSentFalseAndTriggerAtBefore(OffsetDateTime now, Pageable pageable);
}
