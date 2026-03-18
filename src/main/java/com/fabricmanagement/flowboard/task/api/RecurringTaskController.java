package com.fabricmanagement.flowboard.task.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.app.RecurringTaskService;
import com.fabricmanagement.flowboard.task.domain.RecurringTaskTemplate;
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

/**
 * [O1 FIX] @PreAuthorize eklendi. [O8 FIX] Controller artık RecurringTaskService üzerinden veri
 * alıyor.
 */
@RestController
@RequestMapping("/api/v1/flowboard/recurring-templates")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class RecurringTaskController {

  private final RecurringTaskService recurringTaskService;

  @GetMapping("/boards/{boardId}")
  public ResponseEntity<List<RecurringTaskTemplate>> getTemplatesForBoard(
      @PathVariable @NotNull UUID boardId) {
    return ResponseEntity.ok(
        recurringTaskService.getTemplatesForBoard(TenantContext.getCurrentTenantId(), boardId));
  }
}
