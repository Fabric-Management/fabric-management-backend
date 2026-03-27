package com.fabricmanagement.flowboard.task.api;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flowboard/tasks")
@RequiredArgsConstructor
@Tag(name = "FlowBoard — Task", description = "Task yönetimi ve durum güncellemeleri")
public class TaskController {

  private final TaskService taskService;
  private final Clock clock;

  @GetMapping("/{id}")
  @Operation(summary = "Task detayı")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<TaskResponse>> getTask(@PathVariable UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(TaskResponse.from(taskService.getTask(id), LocalDate.now(clock))));
  }

  @PostMapping
  @Operation(summary = "Manuel task oluştur")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<TaskResponse>> createTask(
      @Valid @RequestBody CreateTaskRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                TaskResponse.from(taskService.createTask(req), LocalDate.now(clock))));
  }

  @PutMapping("/{id}/status")
  @Operation(summary = "Task status güncelle — WIP kontrolü ile")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<TaskResponse>> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody UpdateTaskStatusRequest req) {
    var userCtx = currentUser();
    boolean isManager = hasRole("ROLE_MANAGER") || hasRole("ROLE_ADMIN");
    return ResponseEntity.ok(
        ApiResponse.success(
            TaskResponse.from(
                taskService.updateStatus(id, req, userCtx.userId(), isManager),
                LocalDate.now(clock))));
  }

  @PutMapping("/{id}/assign")
  @Operation(summary = "Task'ı kullanıcıya ata")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<Void>> assignTask(
      @PathVariable UUID id,
      @RequestParam UUID userId,
      @RequestParam(defaultValue = "MANAGER") AssignedBy assignedBy) {
    var userCtx = currentUser();
    if (assignedBy == AssignedBy.SELF && !userId.equals(userCtx.userId())) {
      throw new FlowBoardDomainException("SELF assignment can only be used to assign yourself");
    }
    if (assignedBy == AssignedBy.MANAGER && !hasRole("ROLE_MANAGER") && !hasRole("ROLE_ADMIN")) {
      throw new AccessDeniedException("Only managers or admins can assign tasks as MANAGER");
    }
    taskService.assignToUser(id, userId, assignedBy, userCtx.userId());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Task'ı iptal et (soft cancel)")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> cancelTask(@PathVariable UUID id) {
    taskService.cancelTask(id);
    return ResponseEntity.noContent().build();
  }

  private AuthenticatedUserContext currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    throw new NotFoundException("AuthenticatedUserContext not found in SecurityContext");
  }

  private boolean hasRole(String role) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return false;
    return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
  }
}
