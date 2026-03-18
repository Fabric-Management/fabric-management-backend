package com.fabricmanagement.flowboard.dashboard.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.dashboard.app.PerformanceService;
import com.fabricmanagement.flowboard.dashboard.domain.UserPerformanceSnapshot;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [O1 FIX] @PreAuthorize eklendi. [O7 FIX] Controller artık PerformanceService üzerinden veri
 * alıyor.
 */
@RestController
@RequestMapping("/api/v1/flowboard/performance")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PerformanceController {

  private final PerformanceService performanceService;

  @GetMapping("/leaderboard")
  public ResponseEntity<List<UserPerformanceSnapshot>> getLeaderboard(
      @RequestParam("date") @NotNull LocalDate date) {
    return ResponseEntity.ok(
        performanceService.getLeaderboard(TenantContext.getCurrentTenantId(), date));
  }
}
