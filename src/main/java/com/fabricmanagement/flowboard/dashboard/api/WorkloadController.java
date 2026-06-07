package com.fabricmanagement.flowboard.dashboard.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.flowboard.dashboard.app.WorkloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flowboard/workloads")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "FlowBoard — Workload", description = "Kullanıcı iş yükü analizi")
public class WorkloadController {

  private final WorkloadService workloadService;

  @GetMapping("/users/{userId}")
  @Operation(summary = "Kullanıcının anlık iş yükünü getir")
  public ResponseEntity<ApiResponse<WorkloadService.UserWorkload>> getUserWorkload(
      @PathVariable @NotNull UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    return ResponseEntity.ok(
        ApiResponse.success(workloadService.getUserWorkload(tenantId, userId)));
  }
}
