package com.fabricmanagement.flowboard.task.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.task.app.TaskDetailService;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.AssignedBy;
import com.fabricmanagement.flowboard.task.dto.AddTaskCommentRequest;
import com.fabricmanagement.flowboard.task.dto.CreateTaskRequest;
import com.fabricmanagement.flowboard.task.dto.TaskCommentResponse;
import com.fabricmanagement.flowboard.task.dto.TaskResponse;
import com.fabricmanagement.flowboard.task.dto.UpdateTaskStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
  private final TaskDetailService taskDetailService;
  private final Clock clock;

  @GetMapping("/{id}/comments")
  @Operation(summary = "Task yorumlarını listele")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'read')")
  public ResponseEntity<ApiResponse<List<TaskCommentResponse>>> getTaskComments(
      @PathVariable UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(taskDetailService.getComments(TenantContext.requireTenantId(), id)));
  }

  @PostMapping("/{id}/comments")
  @Operation(summary = "Task'a yorum ekle")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'write')")
  public ResponseEntity<ApiResponse<TaskCommentResponse>> addTaskComment(
      @PathVariable UUID id, @Valid @RequestBody AddTaskCommentRequest req) {
    var userCtx = currentUser();
    var created =
        taskDetailService.addCommentFromRequest(
            TenantContext.requireTenantId(), id, userCtx.userId(), req);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Task detayı")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'read')")
  public ResponseEntity<ApiResponse<TaskResponse>> getTask(@PathVariable UUID id) {
    var task = taskService.getTask(id);
    return ResponseEntity.ok(
        ApiResponse.success(
            taskService
                .mapToTaskResponses(java.util.List.of(task), LocalDate.now(clock))
                .getFirst()));
  }

  @PostMapping
  @Operation(summary = "Manuel task oluştur")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'write')")
  public ResponseEntity<ApiResponse<TaskResponse>> createTask(
      @Valid @RequestBody CreateTaskRequest req) {
    var task = taskService.createTask(req);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                taskService
                    .mapToTaskResponses(java.util.List.of(task), LocalDate.now(clock))
                    .getFirst()));
  }

  @PutMapping("/{id}/status")
  @Operation(summary = "Task status güncelle — WIP kontrolü ile")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'write')")
  public ResponseEntity<ApiResponse<TaskResponse>> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody UpdateTaskStatusRequest req) {
    var userCtx = currentUser();
    boolean isManager = hasRole("ROLE_MANAGER") || hasRole("ROLE_ADMIN");
    var task = taskService.updateStatus(id, req, userCtx.userId(), isManager);
    return ResponseEntity.ok(
        ApiResponse.success(
            taskService
                .mapToTaskResponses(java.util.List.of(task), LocalDate.now(clock))
                .getFirst()));
  }

  @PutMapping("/{id}/assign")
  @Operation(summary = "Task'ı kullanıcıya ata")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'write')")
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

  @GetMapping("/{id}/assignees")
  @Operation(summary = "Task'a atanmış kişileri getir")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'read')")
  public ResponseEntity<
          ApiResponse<java.util.List<com.fabricmanagement.flowboard.task.dto.TaskAssigneeResponse>>>
      getAssignees(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(taskService.getTaskAssignees(id)));
  }

  @DeleteMapping("/{id}/assignees/{userId}")
  @Operation(summary = "Task'tan atamayı kaldır")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'write')")
  public ResponseEntity<ApiResponse<Void>> unassignUser(
      @PathVariable UUID id, @PathVariable UUID userId) {
    var userCtx = currentUser();
    taskService.unassignUser(id, userId, userCtx.userId());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Task'ı iptal et (soft cancel)")
  @PreAuthorize("@auth.can(authentication, 'flowboard', 'write')")
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
