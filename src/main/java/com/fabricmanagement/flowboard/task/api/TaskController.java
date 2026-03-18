package com.fabricmanagement.flowboard.task.api;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.AssignedBy;
import com.fabricmanagement.flowboard.task.dto.CreateTaskRequest;
import com.fabricmanagement.flowboard.task.dto.TaskResponse;
import com.fabricmanagement.flowboard.task.dto.UpdateTaskStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * FlowBoard task yönetimi API.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md}
 */
@RestController
@RequestMapping("/api/flowboard/tasks")
@RequiredArgsConstructor
@Tag(name = "FlowBoard — Task", description = "Task yönetimi ve durum güncellemeleri")
public class TaskController {

  private final TaskService taskService;
  private final Clock clock;

  @GetMapping("/{id}")
  @Operation(summary = "Task detayı")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id) {
    return ResponseEntity.ok(TaskResponse.from(taskService.getTask(id), LocalDate.now(clock)));
  }

  @PostMapping
  @Operation(summary = "Manuel task oluştur")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(TaskResponse.from(taskService.createTask(req), LocalDate.now(clock)));
  }

  @PutMapping("/{id}/status")
  @Operation(summary = "Task status güncelle — WIP kontrolü ile")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TaskResponse> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody UpdateTaskStatusRequest req) {
    // [S3 FIX] isManager kontrolü Spring Security'den alınıyor
    var userCtx = currentUser();
    boolean isManager = hasRole("ROLE_MANAGER") || hasRole("ROLE_ADMIN");
    return ResponseEntity.ok(
        TaskResponse.from(
            taskService.updateStatus(id, req, userCtx.userId(), isManager), LocalDate.now(clock)));
  }

  @PutMapping("/{id}/assign")
  @Operation(summary = "Task'ı kullanıcıya ata")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> assignTask(
      @PathVariable UUID id,
      @RequestParam UUID userId,
      @RequestParam(defaultValue = "MANAGER") AssignedBy assignedBy) {
    // [S4 FIX] SELF atama güvenlik kontrolü
    var userCtx = currentUser();
    if (assignedBy == AssignedBy.SELF && !userId.equals(userCtx.userId())) {
      throw new IllegalArgumentException("SELF assignment can only be used to assign yourself");
    }
    // MANAGER atama yetki kontrolü
    if (assignedBy == AssignedBy.MANAGER && !hasRole("ROLE_MANAGER") && !hasRole("ROLE_ADMIN")) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Only managers or admins can assign tasks as MANAGER");
    }
    taskService.assignToUser(id, userId, assignedBy, userCtx.userId());
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Task'ı iptal et (soft cancel)")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> cancelTask(@PathVariable UUID id) {
    taskService.cancelTask(id);
    return ResponseEntity.noContent().build();
  }

  // ---- Yardımcı ----

  private AuthenticatedUserContext currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    throw new IllegalStateException("AuthenticatedUserContext not found in SecurityContext");
  }

  private boolean hasRole(String role) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return false;
    return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
  }
}
