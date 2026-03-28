package com.fabricmanagement.flowboard.dashboard.app;

import com.fabricmanagement.flowboard.dashboard.dto.AssigneeWorkloadDto;
import com.fabricmanagement.flowboard.dashboard.dto.BoardMetricsResponse;
import com.fabricmanagement.flowboard.dashboard.dto.MetricWithTrend;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.flowboard.task.domain.event.TaskAssignedEvent;
import com.fabricmanagement.flowboard.task.domain.event.TaskCreatedEvent;
import com.fabricmanagement.flowboard.task.domain.event.TaskStatusChangedEvent;
import com.fabricmanagement.flowboard.task.domain.event.TaskUnassignedEvent;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.dto.UserDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardDashboardService {

  private final TaskRepository taskRepository;
  private final UserFacade userFacade;
  private final CacheManager cacheManager;

  @Transactional(readOnly = true)
  @Cacheable(
      value = "boardMetrics",
      key = "#tenantId.toString() + '_' + #boardId.toString()",
      unless = "#result == null")
  public BoardMetricsResponse getDashboardMetrics(UUID tenantId, UUID boardId) {
    long startTime = System.currentTimeMillis();

    // 1. Snapshot Metrics (Grouping)
    List<Object[]> snapshotRaw = taskRepository.findTaskMetricsSnapshot(tenantId, boardId);

    int activeCount = 0;
    int overdueCount = 0;
    Map<TaskStatus, Integer> byStatus = new HashMap<>();
    Map<Priority, Integer> byPriority = new HashMap<>();

    for (Object[] row : snapshotRaw) {
      TaskStatus status = (TaskStatus) row[0];
      Priority priority = (Priority) row[1];
      boolean isOverdue = (Boolean) row[2];
      int count = ((Number) row[3]).intValue();
      byStatus.put(status, byStatus.getOrDefault(status, 0) + count);
      byPriority.put(priority, byPriority.getOrDefault(priority, 0) + count);

      if (status != TaskStatus.DONE && status != TaskStatus.CANCELLED) {
        activeCount += count;
        if (isOverdue) {
          overdueCount += count;
        }
      }
    }

    // 2. Trend Metrics (Time based counts)
    Instant currStart = Instant.now().minus(7, ChronoUnit.DAYS);
    Instant prevStart = currStart.minus(7, ChronoUnit.DAYS);

    Object[] trends = taskRepository.findTaskTrends(tenantId, boardId, currStart, prevStart);
    Object[] trendRow =
        trends != null && trends.length > 0 && trends[0] instanceof Object[]
            ? (Object[]) trends[0]
            : trends;

    long createdCurrTemp =
        trendRow != null && trendRow[0] != null ? ((Number) trendRow[0]).longValue() : 0;
    long createdPrevTemp =
        trendRow != null && trendRow[1] != null ? ((Number) trendRow[1]).longValue() : 0;
    long completedCurrTemp =
        trendRow != null && trendRow[2] != null ? ((Number) trendRow[2]).longValue() : 0;
    long completedPrevTemp =
        trendRow != null && trendRow[3] != null ? ((Number) trendRow[3]).longValue() : 0;

    int totalCurr = (int) createdCurrTemp;
    int totalPrev = (int) createdPrevTemp;
    int completedCurr = (int) completedCurrTemp;
    int completedPrev = (int) completedPrevTemp;

    // The user also wants total lifetime completed vs active for the "totalTasks" card, or just
    // 7-day created?
    // Let's use overall counts from grouping for current state, and 7-day creations for trends.
    int lifetimeTotal = byStatus.values().stream().mapToInt(Integer::intValue).sum();
    int lifetimeCompleted = byStatus.getOrDefault(TaskStatus.DONE, 0);

    MetricWithTrend totalTasks =
        MetricWithTrend.of(lifetimeTotal, lifetimeTotal - totalCurr + totalPrev); // approximated
    MetricWithTrend activeTasks =
        MetricWithTrend.of(
            activeCount,
            activeCount); // Trend computing for point-in-time active is complex, omitted for now
    MetricWithTrend completedTasksM =
        MetricWithTrend.of(lifetimeCompleted, lifetimeCompleted - completedCurr + completedPrev);
    MetricWithTrend overdueTasksM = MetricWithTrend.of(overdueCount, overdueCount);

    double completionRate =
        lifetimeTotal == 0 ? 0.0 : ((double) lifetimeCompleted / lifetimeTotal) * 100.0;

    // 3. Assignee Workload
    List<Object[]> assigneeRaw = taskRepository.findAssigneeWorkloadSnapshot(tenantId, boardId);

    // Fetch user names efficiently
    List<UserDto> allUsers = userFacade.findByTenant(tenantId);
    Map<UUID, String> userNames =
        allUsers.stream()
            .collect(
                Collectors.toMap(UserDto::getId, u -> u.getFirstName() + " " + u.getLastName()));

    List<AssigneeWorkloadDto> workload =
        assigneeRaw.stream()
            .map(
                row -> {
                  UUID userId = (UUID) row[0];
                  int count = ((Number) row[1]).intValue();
                  String name = userNames.getOrDefault(userId, "Unknown User");
                  return new AssigneeWorkloadDto(userId, name, count);
                })
            .toList();

    log.debug(
        "Dashboard metrics computed in {}ms for board {}",
        (System.currentTimeMillis() - startTime),
        boardId);

    return new BoardMetricsResponse(
        totalTasks,
        activeTasks,
        completedTasksM,
        overdueTasksM,
        completionRate,
        byStatus,
        byPriority,
        workload,
        Instant.now());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @CacheEvict(
      value = "boardMetrics",
      key = "#event.getTenantId().toString() + '_' + #event.getBoardId().toString()")
  public void onTaskCreated(TaskCreatedEvent event) {
    log.debug(
        "Evicting boardMetrics cache for board {} due to TaskCreatedEvent", event.getBoardId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @CacheEvict(
      value = "boardMetrics",
      key = "#event.getTenantId().toString() + '_' + #event.getBoardId().toString()")
  public void onTaskStatusChanged(TaskStatusChangedEvent event) {
    log.debug(
        "Evicting boardMetrics cache for board {} due to TaskStatusChangedEvent",
        event.getBoardId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskAssigned(TaskAssignedEvent event) {
    taskRepository
        .findById(event.getTaskId())
        .ifPresent(
            task -> {
              Cache cache = cacheManager.getCache("boardMetrics");
              if (cache != null) {
                cache.evict(event.getTenantId().toString() + "_" + task.getBoardId().toString());
                log.debug(
                    "Evicting boardMetrics cache for board {} due to TaskAssignedEvent",
                    task.getBoardId());
              }
            });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskUnassigned(TaskUnassignedEvent event) {
    taskRepository
        .findById(event.getTaskId())
        .ifPresent(
            task -> {
              Cache cache = cacheManager.getCache("boardMetrics");
              if (cache != null) {
                cache.evict(event.getTenantId().toString() + "_" + task.getBoardId().toString());
                log.debug(
                    "Evicting boardMetrics cache for board {} due to TaskUnassignedEvent",
                    task.getBoardId());
              }
            });
  }
}
