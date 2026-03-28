package com.fabricmanagement.flowboard.dashboard.dto;

import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record BoardMetricsResponse(
    MetricWithTrend totalTasks,
    MetricWithTrend activeTasks,
    MetricWithTrend completedTasks,
    MetricWithTrend overdueTasks,
    double completionRate,
    Map<TaskStatus, Integer> tasksByStatus,
    Map<Priority, Integer> tasksByPriority,
    List<AssigneeWorkloadDto> workloadByAssignee,
    Instant calculatedAt) {}
