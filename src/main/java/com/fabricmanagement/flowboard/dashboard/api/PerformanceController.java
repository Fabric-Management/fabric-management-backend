package com.fabricmanagement.flowboard.dashboard.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.flowboard.dashboard.app.PerformanceService;
import com.fabricmanagement.flowboard.dashboard.dto.UserPerformanceSnapshotDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@RequestMapping("/api/v1/flowboard/performance")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "FlowBoard — Performance", description = "Kullanıcı performans liderlik tablosu")
public class PerformanceController {

  private final PerformanceService performanceService;

  @GetMapping("/leaderboard")
  @Operation(summary = "Haftalık performans liderlik tablosu")
  public ResponseEntity<ApiResponse<List<UserPerformanceSnapshotDto>>> getLeaderboard(
      @RequestParam("date") @NotNull LocalDate date) {
    List<UserPerformanceSnapshotDto> result =
        performanceService.getLeaderboard(TenantContext.requireTenantId(), date).stream()
            .map(UserPerformanceSnapshotDto::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(result));
  }
}
