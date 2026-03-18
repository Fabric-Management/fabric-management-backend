package com.fabricmanagement.flowboard.dashboard.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.dashboard.app.WorkloadService;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** [O1 FIX] @PreAuthorize eklendi. */
@RestController
@RequestMapping("/api/v1/flowboard/workloads")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WorkloadController {

  private final WorkloadService workloadService;

  @GetMapping("/users/{userId}")
  public ResponseEntity<WorkloadService.UserWorkload> getUserWorkload(
      @PathVariable @NotNull UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return ResponseEntity.ok(workloadService.getUserWorkload(tenantId, userId));
  }
}
