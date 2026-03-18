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

  // [K4 FIX] WorkloadService için gerçek sorgular
  @Query(
      "SELECT COUNT(t) FROM Task t WHERE t.tenantId = :tenantId AND t.isActive = true AND t.assigneeUserId = :userId AND t.status NOT IN (com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE, com.fabricmanagement.flowboard.task.domain.TaskStatus.CANCELLED)")
  long countActiveTasksForUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Query(
      "SELECT COALESCE(SUM(t.estimatedHours), 0) FROM Task t WHERE t.tenantId = :tenantId AND t.isActive = true AND t.assigneeUserId = :userId AND t.status NOT IN (com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE, com.fabricmanagement.flowboard.task.domain.TaskStatus.CANCELLED)")
  java.math.BigDecimal sumEstimatedHoursForUser(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  // [K2 FIX] PerformanceJob: Tüm aktif kullanıcıları bul
  @Query(
      "SELECT DISTINCT t.assigneeUserId FROM Task t WHERE t.tenantId = :tenantId AND t.isActive = true AND t.assigneeUserId IS NOT NULL")
  List<UUID> findDistinctAssigneeUserIds(@Param("tenantId") UUID tenantId);

  // [K2 FIX] Kullanıcının belirli tarih aralığında tamamladığı task sayısı
  @Query(
      "SELECT COUNT(t) FROM Task t WHERE t.tenantId = :tenantId AND t.assigneeUserId = :userId AND t.status = com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE AND t.updatedAt BETWEEN :startDate AND :endDate")
  int countCompletedTasksInPeriod(
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("startDate") java.time.Instant startDate,
      @Param("endDate") java.time.Instant endDate);

  // [K2 FIX] Kullanıcının belirli tarih aralığında geciken task sayısı
  @Query(
      "SELECT COUNT(t) FROM Task t WHERE t.tenantId = :tenantId AND t.assigneeUserId = :userId AND t.status = com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE AND t.deadline < CAST(t.updatedAt AS LocalDate) AND t.updatedAt BETWEEN :startDate AND :endDate")
  int countOverdueCompletedTasksInPeriod(
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("startDate") java.time.Instant startDate,
      @Param("endDate") java.time.Instant endDate);
}
