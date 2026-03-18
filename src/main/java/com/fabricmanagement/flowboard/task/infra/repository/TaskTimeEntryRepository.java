package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskTimeEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskTimeEntryRepository extends JpaRepository<TaskTimeEntry, UUID> {

  List<TaskTimeEntry> findAllByTaskIdOrderByStartedAtDesc(UUID taskId);

  // Partial index ile korunan tek aktif zamanlayıcıyı bulur
  @Query(
      "SELECT t FROM TaskTimeEntry t WHERE t.userId = :userId AND t.endedAt IS NULL AND t.deletedAt IS NULL")
  Optional<TaskTimeEntry> findActiveTimerByUserId(@Param("userId") UUID userId);

  // Raporlamalar için task üzerindeki toplam loglanmış süreyi getirir
  @Query(
      "SELECT SUM(t.durationMinutes) FROM TaskTimeEntry t WHERE t.taskId = :taskId AND t.deletedAt IS NULL")
  Integer sumDurationByTaskId(@Param("taskId") UUID taskId);
}
