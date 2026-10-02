package com.fabricmanagement.production.core.workorder.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.core.workorder.app.WorkOrderHoldService;
import com.fabricmanagement.production.core.workorder.dto.WorkOrderHoldDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

/** Confirming and resuming holds on running work (SOI D8, A12). */
@RestController
@RequestMapping("/api/v1/production/work-order-holds")
@RequiredArgsConstructor
@Tag(name = "Work Order Holds", description = "Hold requests, physical stop and resume")
public class WorkOrderHoldController {

  private final WorkOrderHoldService holds;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(operationId = "listOpenWorkOrderHolds", summary = "Requested and confirmed holds")
  public ResponseEntity<ApiResponse<List<WorkOrderHoldDtos.HoldDto>>> open() {
    return ResponseEntity.ok(ApiResponse.success(holds.open()));
  }

  @PostMapping("/{holdId}/confirm")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "confirmWorkOrderHold",
      summary = "Confirm that the work physically stopped")
  public ResponseEntity<ApiResponse<WorkOrderHoldDtos.HoldDto>> confirm(
      @PathVariable UUID holdId, @Valid @RequestBody WorkOrderHoldDtos.ConfirmStop request) {
    return ResponseEntity.ok(ApiResponse.success(holds.confirmStop(holdId, request)));
  }

  @PostMapping("/{holdId}/resume")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "resumeWorkOrderHold",
      summary = "Resume after the customer change is settled and the checks are complete")
  public ResponseEntity<ApiResponse<WorkOrderHoldDtos.HoldDto>> resume(
      @PathVariable UUID holdId, @Valid @RequestBody WorkOrderHoldDtos.Resume request) {
    return ResponseEntity.ok(ApiResponse.success(holds.resume(holdId, request)));
  }
}
