package com.fabricmanagement.flowboard.dashboard.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.flowboard.dashboard.api.mapper.DashboardMapper;
import com.fabricmanagement.flowboard.dashboard.app.DashboardService;
import com.fabricmanagement.flowboard.dashboard.domain.DashboardConfig;
import com.fabricmanagement.flowboard.dashboard.domain.DashboardWidget;
import com.fabricmanagement.flowboard.dashboard.dto.AddWidgetRequest;
import com.fabricmanagement.flowboard.dashboard.dto.DashboardConfigDto;
import com.fabricmanagement.flowboard.dashboard.dto.DashboardWidgetDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard yönetim API'si.
 *
 * <p>[O1 FIX] @PreAuthorize eklenmiştir.
 *
 * <p>[O2 FIX] @Valid + validation annotation'ları eklenmiştir.
 *
 * <p>[O3 FIX] Entity yerine DTO'lar dönülüyor.
 */
@RestController
@RequestMapping("/api/v1/flowboard/dashboards")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(
    name = "FlowBoard — Dashboard",
    description = "Kullanici dashboard yapilandirmasi ve widget yonetimi")
public class DashboardController {

  private final DashboardService dashboardService;

  @GetMapping("/default")
  @Operation(summary = "Varsayilan dashboard'u getir")
  public ResponseEntity<ApiResponse<DashboardConfigDto>> getDefaultDashboard(
      @RequestParam("userId") @NotNull UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    return ResponseEntity.ok(
        ApiResponse.success(
            DashboardMapper.toDto(
                dashboardService
                    .getDefaultDashboard(tenantId, userId)
                    .orElseThrow(
                        () ->
                            new EntityNotFoundException(
                                "Default dashboard not found for user: " + userId)))));
  }

  @GetMapping("/{dashboardId}/widgets")
  public ResponseEntity<ApiResponse<List<DashboardWidgetDto>>> getWidgets(
      @PathVariable @NotNull UUID dashboardId) {
    UUID tenantId = TenantContext.requireTenantId();
    List<DashboardWidget> widgets = dashboardService.getDashboardWidgets(tenantId, dashboardId);
    return ResponseEntity.ok(
        ApiResponse.success(widgets.stream().map(DashboardMapper::toDto).toList()));
  }

  @PostMapping("/default/layout")
  public ResponseEntity<ApiResponse<DashboardConfigDto>> updateDefaultLayout(
      @RequestParam("userId") @NotNull UUID userId, @RequestBody @NotBlank String layoutJsonb) {
    UUID tenantId = TenantContext.requireTenantId();
    DashboardConfig config =
        dashboardService.createOrUpdateDefaultDashboard(tenantId, userId, layoutJsonb);
    return ResponseEntity.ok(ApiResponse.success(DashboardMapper.toDto(config)));
  }

  @PostMapping("/{dashboardId}/widgets")
  public ResponseEntity<ApiResponse<DashboardWidgetDto>> addWidget(
      @PathVariable @NotNull UUID dashboardId, @RequestBody @Valid AddWidgetRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    DashboardWidget widget =
        dashboardService.addWidgetToDashboard(
            tenantId,
            dashboardId,
            request.type(),
            request.title(),
            request.configJsonb(),
            request.displayOrder());
    return ResponseEntity.ok(ApiResponse.success(DashboardMapper.toDto(widget)));
  }
}
