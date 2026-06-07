package com.fabricmanagement.flowboard.task.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.flowboard.task.app.RecurringTaskService;
import com.fabricmanagement.flowboard.task.dto.RecurringTaskTemplateDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flowboard/recurring-templates")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "FlowBoard — Recurring Tasks", description = "Tekrarlayan görev şablonları")
public class RecurringTaskController {

  private final RecurringTaskService recurringTaskService;

  @GetMapping("/boards/{boardId}")
  @Operation(summary = "Board'a ait tekrarlayan görev şablonları")
  public ResponseEntity<ApiResponse<List<RecurringTaskTemplateDto>>> getTemplatesForBoard(
      @PathVariable @NotNull UUID boardId) {
    List<RecurringTaskTemplateDto> result =
        recurringTaskService.getTemplatesForBoard(TenantContext.requireTenantId(), boardId).stream()
            .map(RecurringTaskTemplateDto::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(result));
  }
}
