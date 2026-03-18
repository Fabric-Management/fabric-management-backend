package com.fabricmanagement.flowboard.dashboard.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.flowboard.dashboard.domain.WorkloadStatus;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * D1 FIX: WorkloadService testleri genişletildi. Artık gerçek TaskRepository mock'u kullanılıyor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkloadService")
class WorkloadServiceTest {

  @Mock private TaskRepository taskRepo;
  @InjectMocks private WorkloadService workloadService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @Test
  @DisplayName("Düşük iş yükü → AVAILABLE")
  void getUserWorkload_lowHours_returnsAvailable() {
    when(taskRepo.countActiveTasksForUser(tenantId, userId)).thenReturn(2L);
    when(taskRepo.sumEstimatedHoursForUser(tenantId, userId)).thenReturn(new BigDecimal("20.0"));

    WorkloadService.UserWorkload workload = workloadService.getUserWorkload(tenantId, userId);

    assertThat(workload.activeTaskCount()).isEqualTo(2L);
    assertThat(workload.totalEstimatedHours()).isEqualByComparingTo(new BigDecimal("20.0"));
    assertThat(workload.status()).isEqualTo(WorkloadStatus.AVAILABLE);
  }

  @Test
  @DisplayName("Orta iş yükü → OPTIMAL")
  void getUserWorkload_mediumHours_returnsOptimal() {
    when(taskRepo.countActiveTasksForUser(tenantId, userId)).thenReturn(5L);
    when(taskRepo.sumEstimatedHoursForUser(tenantId, userId)).thenReturn(new BigDecimal("38.5"));

    WorkloadService.UserWorkload workload = workloadService.getUserWorkload(tenantId, userId);

    assertThat(workload.status()).isEqualTo(WorkloadStatus.OPTIMAL);
  }

  @Test
  @DisplayName("Yüksek iş yükü → OVERLOADED")
  void getUserWorkload_highHours_returnsOverloaded() {
    when(taskRepo.countActiveTasksForUser(tenantId, userId)).thenReturn(12L);
    when(taskRepo.sumEstimatedHoursForUser(tenantId, userId)).thenReturn(new BigDecimal("50.0"));

    WorkloadService.UserWorkload workload = workloadService.getUserWorkload(tenantId, userId);

    assertThat(workload.status()).isEqualTo(WorkloadStatus.OVERLOADED);
  }

  @Test
  @DisplayName("Sınır değer: Tam 35 saat → OPTIMAL (not AVAILABLE)")
  void getUserWorkload_exactOptimalThreshold_returnsOptimal() {
    when(taskRepo.countActiveTasksForUser(tenantId, userId)).thenReturn(4L);
    when(taskRepo.sumEstimatedHoursForUser(tenantId, userId)).thenReturn(new BigDecimal("35.0"));

    WorkloadService.UserWorkload workload = workloadService.getUserWorkload(tenantId, userId);

    assertThat(workload.status()).isEqualTo(WorkloadStatus.OPTIMAL);
  }

  @Test
  @DisplayName("Sınır değer: Tam 45 saat → OPTIMAL (not OVERLOADED)")
  void getUserWorkload_exactOverloadThreshold_returnsOptimal() {
    when(taskRepo.countActiveTasksForUser(tenantId, userId)).thenReturn(8L);
    when(taskRepo.sumEstimatedHoursForUser(tenantId, userId)).thenReturn(new BigDecimal("45.0"));

    WorkloadService.UserWorkload workload = workloadService.getUserWorkload(tenantId, userId);

    assertThat(workload.status()).isEqualTo(WorkloadStatus.OPTIMAL);
  }

  @Test
  @DisplayName("Sıfır saat → AVAILABLE")
  void getUserWorkload_zeroHours_returnsAvailable() {
    when(taskRepo.countActiveTasksForUser(tenantId, userId)).thenReturn(0L);
    when(taskRepo.sumEstimatedHoursForUser(tenantId, userId)).thenReturn(BigDecimal.ZERO);

    WorkloadService.UserWorkload workload = workloadService.getUserWorkload(tenantId, userId);

    assertThat(workload.activeTaskCount()).isEqualTo(0L);
    assertThat(workload.status()).isEqualTo(WorkloadStatus.AVAILABLE);
  }
}
