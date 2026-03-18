package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Task status güncelleme isteği.
 *
 * @param newStatus Hedef status
 */
public record UpdateTaskStatusRequest(@NotNull TaskStatus newStatus) {}
