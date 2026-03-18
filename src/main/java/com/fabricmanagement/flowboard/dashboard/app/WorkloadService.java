package com.fabricmanagement.flowboard.dashboard.app;

import com.fabricmanagement.flowboard.dashboard.domain.WorkloadStatus;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kullanıcıların anlık iş yükünü hesaplar.
 *
 * <p>[K4 FIX] Stub veri kaldırıldı, gerçek TaskRepository sorguları kullanılıyor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkloadService {

  private final TaskRepository taskRepo;

  // Haftalık varsayılan aktif çalışma kapasitesi threshold'u (saat)
  private static final BigDecimal OPTIMAL_THRESHOLD = new BigDecimal("35.0");
  private static final BigDecimal OVERLOAD_THRESHOLD = new BigDecimal("45.0");

  /** Kullanıcının anlık iş yükünü hesaplar */
  @Transactional(readOnly = true)
  public UserWorkload getUserWorkload(UUID tenantId, UUID userId) {
    long activeTaskCount = taskRepo.countActiveTasksForUser(tenantId, userId);
    BigDecimal totalEstimatedHours = taskRepo.sumEstimatedHoursForUser(tenantId, userId);

    WorkloadStatus status = calculateStatus(totalEstimatedHours);
    return new UserWorkload(activeTaskCount, totalEstimatedHours, status);
  }

  private WorkloadStatus calculateStatus(BigDecimal hours) {
    if (hours.compareTo(OPTIMAL_THRESHOLD) < 0) return WorkloadStatus.AVAILABLE;
    if (hours.compareTo(OVERLOAD_THRESHOLD) > 0) return WorkloadStatus.OVERLOADED;
    return WorkloadStatus.OPTIMAL;
  }

  public record UserWorkload(
      long activeTaskCount, BigDecimal totalEstimatedHours, WorkloadStatus status) {}
}
