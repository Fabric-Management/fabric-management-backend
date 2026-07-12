package com.fabricmanagement.common.infrastructure.events.api;

import com.fabricmanagement.common.infrastructure.events.FollowUpFeedbackService;
import com.fabricmanagement.common.infrastructure.events.FollowUpFlagDto;
import com.fabricmanagement.common.infrastructure.events.FollowUpFlagService;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/follow-up-flags")
@RequiredArgsConstructor
@Tag(name = "Follow-up flags", description = "Incomplete background follow-up flags")
public class FollowUpFlagController {

  private final FollowUpFlagService flagService;
  private final FollowUpFeedbackService feedbackService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "List active follow-up flags for a record")
  public ResponseEntity<ApiResponse<List<FollowUpFlagDto>>> findForRecord(
      @RequestParam String entityType, @RequestParam UUID entityId) {
    return ResponseEntity.ok(
        ApiResponse.success(flagService.findActiveForRecord(entityType, entityId)));
  }

  @GetMapping("/active")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "List active follow-up flags for the current tenant")
  public ResponseEntity<ApiResponse<List<FollowUpFlagDto>>> findActive() {
    return ResponseEntity.ok(ApiResponse.success(flagService.findActiveForTenant()));
  }

  @PostMapping("/{flagId}/feedback")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Report an incomplete follow-up to operations")
  public ResponseEntity<ApiResponse<Void>> reportFeedback(@PathVariable UUID flagId) {
    feedbackService.report(flagId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
