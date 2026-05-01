package com.fabricmanagement.flowboard.dashboard.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.flowboard.dashboard.app.BoardDashboardService;
import com.fabricmanagement.flowboard.dashboard.dto.BoardMetricsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flowboard/boards/{boardId}/dashboard")
@RequiredArgsConstructor
@Tag(name = "FlowBoard — Dashboard", description = "Dashboard widget metrikleri")
@Slf4j
public class BoardDashboardController {

  private final BoardDashboardService dashboardService;

  @GetMapping("/metrics")
  @Operation(summary = "Pano için özet, trend ve iş yükü metriklerini getirir")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'read')")
  public ResponseEntity<ApiResponse<BoardMetricsResponse>> getBoardMetrics(
      @PathVariable UUID boardId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    BoardMetricsResponse metrics = dashboardService.getDashboardMetrics(tenantId, boardId);
    return ResponseEntity.ok(ApiResponse.success(metrics));
  }
}
