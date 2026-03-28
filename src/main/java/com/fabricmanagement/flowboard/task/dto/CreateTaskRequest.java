package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Manuel task oluşturma isteği.
 *
 * @param boardId Bağlı board
 * @param title Task başlığı
 * @param taskType Task tipi
 * @param moduleType Modül tipi
 * @param priority Öncelik (null → MEDIUM)
 * @param deadline Opsiyonel deadline
 * @param estimatedHours Tahmini süre
 * @param entityType Polimorfik referans tipi
 * @param entityId Polimorfik referans ID
 */
public record CreateTaskRequest(
    @NotNull UUID boardId,
    @NotBlank @Size(max = 500) String title,
    String description,
    @NotNull TaskType taskType,
    @NotNull ModuleType moduleType,
    Priority priority,
    LocalDate deadline,
    BigDecimal estimatedHours,
    String entityType,
    UUID entityId,
    String sourceType,
    UUID sourceId) {}
