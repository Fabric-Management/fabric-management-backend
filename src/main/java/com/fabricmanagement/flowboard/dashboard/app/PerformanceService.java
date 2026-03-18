package com.fabricmanagement.flowboard.dashboard.app;

import com.fabricmanagement.flowboard.dashboard.domain.UserPerformanceSnapshot;
import com.fabricmanagement.flowboard.dashboard.infra.repository.UserPerformanceSnapshotRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performans snapshot servis katmanı. [O7 FIX] Controller doğrudan repository kullanmak yerine bu
 * servis aracılığıyla erişiyor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

  private final UserPerformanceSnapshotRepository snapshotRepo;

  @Transactional(readOnly = true)
  public List<UserPerformanceSnapshot> getLeaderboard(UUID tenantId, LocalDate date) {
    return snapshotRepo.getLeaderboard(tenantId, date);
  }
}
