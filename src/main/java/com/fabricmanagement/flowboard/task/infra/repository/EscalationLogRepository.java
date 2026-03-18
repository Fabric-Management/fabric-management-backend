package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.EscalationLog;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EscalationLogRepository extends JpaRepository<EscalationLog, UUID> {

  List<EscalationLog> findAllByTaskIdOrderByCreatedAtDesc(UUID taskId);

  // Belirli bir task ve eskalasyon tipi için son X saat içinde kayıt var mı kontrolü (debounce)
  @Query(
      "SELECT COUNT(e) > 0 FROM EscalationLog e WHERE e.taskId = :taskId AND e.escalationType = :type AND e.createdAt >= :since")
  boolean existsRecentEscalation(
      @Param("taskId") UUID taskId,
      @Param("type") com.fabricmanagement.flowboard.task.domain.EscalationType type,
      @Param("since") OffsetDateTime since);
}
