package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Task repository — FlowBoard'un en kritik sorgu noktası. */
public interface TaskRepository extends JpaRepository<Task, UUID> {

  /**
   * Board'daki tüm aktif task'ları priorityScore'a göre sıralı getirir. Kanban view için
   * kullanılır.
   */
  List<Task> findAllByBoardIdAndIsActiveTrueOrderByPriorityScoreDesc(UUID boardId);

  /** [P2 FIX] Sayfalı board task listesi — büyük board'lar için. */
  @Query(
      """
      SELECT t FROM Task t
      WHERE t.boardId = :boardId AND t.isActive = true
      ORDER BY t.priorityScore DESC
      """)
  Page<Task> findAllByBoardIdAndIsActiveTrue(@Param("boardId") UUID boardId, Pageable pageable);

  /** Server-side filter için dinamik sorgu. */
  @Query(
      """
      SELECT t FROM Task t
      WHERE t.boardId = :boardId AND t.isActive = true
        AND (cast(:priority as string) IS NULL OR t.priority = :priority)
        AND (cast(:moduleType as string) IS NULL OR t.moduleType = :moduleType)
        AND (cast(:assigneeId as string) IS NULL OR EXISTS (
            SELECT 1 FROM TaskAssignee ta
            WHERE ta.taskId = t.id AND ta.isActive = true AND ta.userId = :assigneeId
        ))
      ORDER BY t.priorityScore DESC
      """)
  Page<Task> findAllFiltered(
      @Param("boardId") UUID boardId,
      @Param("priority") com.fabricmanagement.flowboard.task.domain.Priority priority,
      @Param("moduleType") com.fabricmanagement.flowboard.task.domain.ModuleType moduleType,
      @Param("assigneeId") UUID assigneeId,
      Pageable pageable);

  /** Belirli status'e göre board task'larını getirir. */
  List<Task> findAllByBoardIdAndStatusAndIsActiveTrue(UUID boardId, TaskStatus status);

  /** Bir kullanıcıya atanmış açık task'ları getirir — WIP sayımı için. */
  @Query(
      """
      SELECT t FROM Task t
      JOIN TaskAssignee ta ON ta.taskId = t.id
      WHERE ta.userId = :userId
        AND t.status = 'IN_PROGRESS'
        AND t.isActive = true
        AND ta.isActive = true
      """)
  List<Task> findInProgressTasksForUser(@Param("userId") UUID userId);

  /** Bir kullanıcının IN_PROGRESS task sayısı — WIP kontrol için. */
  @Query(
      """
      SELECT COUNT(t) FROM Task t
      JOIN TaskAssignee ta ON ta.taskId = t.id
      WHERE ta.userId = :userId
        AND t.status = 'IN_PROGRESS'
        AND t.isActive = true
        AND ta.isActive = true
      """)
  long countInProgressByUser(@Param("userId") UUID userId);

  /** Polimorfik entity referansına göre bağlı task'ları bulur. */
  List<Task> findAllByEntityTypeAndEntityIdAndIsActiveTrue(String entityType, UUID entityId);

  /**
   * [P2 FIX] Entity + taskType için açık task var mı — idempotency check.
   *
   * <p>Belleğe yüklemeden COUNT sorgusu yapar. DONE ve CANCELLED olanlar hariç tutulur.
   */
  @Query(
      """
      SELECT COUNT(t) > 0 FROM Task t
      WHERE t.entityType = :entityType
        AND t.entityId = :entityId
        AND t.taskType = :taskType
        AND t.isActive = true
        AND t.status NOT IN (com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE,
                             com.fabricmanagement.flowboard.task.domain.TaskStatus.CANCELLED)
      """)
  boolean existsOpenTaskByEntityAndType(
      @Param("entityType") String entityType,
      @Param("entityId") UUID entityId,
      @Param("taskType") com.fabricmanagement.flowboard.task.domain.TaskType taskType);

  /**
   * [L2 FIX] DB sequence ile race-condition safe task numarası üretir. countByTenantId yerine
   * flowboard.task_number_seq kullanılır.
   */
  @Query(value = "SELECT nextval('flowboard.task_number_seq')", nativeQuery = true)
  long getNextTaskNumber();

  /** Schedulers: Süresi geçmiş açık tasklar [K3 FIX: tenant filtresi] [O9 FIX: pagination] */
  @Query(
      """
      SELECT t FROM Task t
      WHERE (:tenantId IS NULL OR t.tenantId = :tenantId)
        AND t.isActive = true
        AND t.status NOT IN (com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE, com.fabricmanagement.flowboard.task.domain.TaskStatus.CANCELLED)
        AND t.deadline < :currentDate
      """)
  Page<Task> findOpenTasksPastDeadline(
      @Param("tenantId") UUID tenantId,
      @Param("currentDate") java.time.LocalDate currentDate,
      Pageable pageable);

  /** Phase 3.1: Yaklaşan deadline'ı olan ve henüz uyarılmamış açık task'ları getirir. */
  @Query(
      """
      SELECT t FROM Task t
      WHERE t.isActive = true
        AND t.status NOT IN (com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE, com.fabricmanagement.flowboard.task.domain.TaskStatus.CANCELLED)
        AND t.isDeadlineWarningFired = false
        AND t.deadline <= :thresholdDate
      """)
  List<Task> findTasksApproachingDeadline(
      @Param("thresholdDate") java.time.LocalDate thresholdDate);

  /**
   * Schedulers: Uzun süredir BLOCKED olan tasklar [K3 FIX: tenant filtresi] [O9 FIX: pagination]
   */
  @Query(
      """
      SELECT t FROM Task t
      WHERE (:tenantId IS NULL OR t.tenantId = :tenantId)
        AND t.isActive = true
        AND t.status = com.fabricmanagement.flowboard.task.domain.TaskStatus.BLOCKED
        AND t.updatedAt < :thresholdTime
      """)
  Page<Task> findBlockedTasksOlderThan(
      @Param("tenantId") UUID tenantId,
      @Param("thresholdTime") java.time.Instant thresholdTime,
      Pageable pageable);

  // WorkloadService icin: kullanicinin aktif task sayisi (TaskAssignee join)
  @Query(
      """
      SELECT COUNT(DISTINCT t) FROM Task t
      JOIN TaskAssignee ta ON ta.taskId = t.id
      WHERE t.tenantId = :tenantId
        AND ta.userId = :userId
        AND ta.isActive = true
        AND t.isActive = true
        AND t.status NOT IN (
          com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE,
          com.fabricmanagement.flowboard.task.domain.TaskStatus.CANCELLED)
      """)
  long countActiveTasksForUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  // WorkloadService icin: kullanicinin aktif task toplam tahmini saat (TaskAssignee join)
  @Query(
      """
      SELECT COALESCE(SUM(t.estimatedHours), 0) FROM Task t
      JOIN TaskAssignee ta ON ta.taskId = t.id
      WHERE t.tenantId = :tenantId
        AND ta.userId = :userId
        AND ta.isActive = true
        AND t.isActive = true
        AND t.status NOT IN (
          com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE,
          com.fabricmanagement.flowboard.task.domain.TaskStatus.CANCELLED)
      """)
  java.math.BigDecimal sumEstimatedHoursForUser(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  // PerformanceJob icin: tenant'taki tum atanmis benzersiz kullanici ID'leri (TaskAssignee join)
  @Query(
      """
      SELECT DISTINCT ta.userId FROM Task t
      JOIN TaskAssignee ta ON ta.taskId = t.id
      WHERE t.tenantId = :tenantId
        AND t.isActive = true
        AND ta.isActive = true
        AND ta.userId IS NOT NULL
      """)
  List<UUID> findDistinctAssigneeUserIds(@Param("tenantId") UUID tenantId);

  // PerformanceJob icin: donem icinde tamamlanan task sayisi (TaskAssignee join)
  @Query(
      """
      SELECT COUNT(DISTINCT t) FROM Task t
      JOIN TaskAssignee ta ON ta.taskId = t.id
      WHERE t.tenantId = :tenantId
        AND ta.userId = :userId
        AND ta.isActive = true
        AND t.status = com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE
        AND t.updatedAt BETWEEN :startDate AND :endDate
      """)
  int countCompletedTasksInPeriod(
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("startDate") java.time.Instant startDate,
      @Param("endDate") java.time.Instant endDate);

  // PerformanceJob icin: donem icinde gecikmiş tamamlanan task sayisi (TaskAssignee join)
  @Query(
      """
      SELECT COUNT(DISTINCT t) FROM Task t
      JOIN TaskAssignee ta ON ta.taskId = t.id
      WHERE t.tenantId = :tenantId
        AND ta.userId = :userId
        AND ta.isActive = true
        AND t.status = com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE
        AND t.deadline IS NOT NULL
        AND t.deadline < CAST(t.updatedAt AS LocalDate)
        AND t.updatedAt BETWEEN :startDate AND :endDate
      """)
  int countOverdueCompletedTasksInPeriod(
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("startDate") java.time.Instant startDate,
      @Param("endDate") java.time.Instant endDate);

  // Dashboard: Task durum, öncelik ve gecikme gruplamalı anlık snapshot
  @Query(
      """
      SELECT t.status, t.priority,
             (CASE WHEN t.deadline IS NOT NULL AND t.deadline < CURRENT_DATE THEN true ELSE false END),
             COUNT(t)
      FROM Task t
      WHERE t.boardId = :boardId AND t.tenantId = :tenantId AND t.isActive = true
      GROUP BY t.status, t.priority,
             (CASE WHEN t.deadline IS NOT NULL AND t.deadline < CURRENT_DATE THEN true ELSE false END)
  """)
  List<Object[]> findTaskMetricsSnapshot(
      @Param("tenantId") UUID tenantId, @Param("boardId") UUID boardId);

  // Dashboard: Zaman bazlı trend metrikleri (Oluşturma ve Tamamlama)
  @Query(
      """
      SELECT
          SUM(CASE WHEN t.createdAt >= :currStart THEN 1 ELSE 0 END),
          SUM(CASE WHEN t.createdAt >= :prevStart AND t.createdAt < :currStart THEN 1 ELSE 0 END),
          SUM(CASE WHEN t.status = com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE AND t.updatedAt >= :currStart THEN 1 ELSE 0 END),
          SUM(CASE WHEN t.status = com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE AND t.updatedAt >= :prevStart AND t.updatedAt < :currStart THEN 1 ELSE 0 END)
      FROM Task t
      WHERE t.boardId = :boardId AND t.tenantId = :tenantId AND t.isActive = true
  """)
  Object[] findTaskTrends(
      @Param("tenantId") UUID tenantId,
      @Param("boardId") UUID boardId,
      @Param("currStart") java.time.Instant currStart,
      @Param("prevStart") java.time.Instant prevStart);

  // Dashboard: Kişi Bazlı İş Yükü (Assignee Workload)
  @Query(
      """
      SELECT ta.userId, COUNT(t)
      FROM Task t
      JOIN TaskAssignee ta ON ta.taskId = t.id
      WHERE t.boardId = :boardId AND t.tenantId = :tenantId
        AND t.isActive = true AND ta.isActive = true
        AND t.status NOT IN (com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE, com.fabricmanagement.flowboard.task.domain.TaskStatus.CANCELLED)
      GROUP BY ta.userId
  """)
  List<Object[]> findAssigneeWorkloadSnapshot(
      @Param("tenantId") UUID tenantId, @Param("boardId") UUID boardId);
}
