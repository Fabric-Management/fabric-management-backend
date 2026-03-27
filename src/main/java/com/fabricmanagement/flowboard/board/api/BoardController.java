package com.fabricmanagement.flowboard.board.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.flowboard.board.app.BoardService;
import com.fabricmanagement.flowboard.board.dto.BoardResponse;
import com.fabricmanagement.flowboard.board.dto.CreateBoardRequest;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.flowboard.task.dto.TaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flowboard/boards")
@RequiredArgsConstructor
@Tag(name = "FlowBoard — Board", description = "Board yönetimi ve kanban görünümü")
public class BoardController {

  private final BoardService boardService;
  private final TaskService taskService;
  private final Clock clock;

  @GetMapping
  @Operation(summary = "Tüm aktif board'ları listele")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<List<BoardResponse>>> getAllBoards() {
    return ResponseEntity.ok(
        ApiResponse.success(
            boardService.getAllBoards().stream().map(BoardResponse::from).toList()));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Board detayı")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<BoardResponse>> getBoard(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(BoardResponse.from(boardService.getBoard(id))));
  }

  @GetMapping("/{id}/tasks")
  @Operation(summary = "Board'daki task'ları sayfalı getir — priorityScore DESC sıralı")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<PagedResponse<TaskResponse>>> getTasksByBoard(
      @PathVariable UUID id, @PageableDefault(size = 30) Pageable pageable) {
    LocalDate today = LocalDate.now(clock);
    PagedResponse<TaskResponse> paged =
        PagedResponse.from(
            taskService.getTasksByBoard(id, pageable).map(t -> TaskResponse.from(t, today)));
    return ResponseEntity.ok(ApiResponse.success(paged));
  }

  @GetMapping("/{id}/kanban")
  @Operation(summary = "Kanban view — status bazlı task grupları")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<Map<String, List<TaskResponse>>>> getKanbanView(
      @PathVariable UUID id) {
    var kanban = taskService.getKanbanView(id);
    LocalDate today = LocalDate.now(clock);
    Map<String, List<TaskResponse>> response = new LinkedHashMap<>();
    for (TaskStatus status : TaskStatus.values()) {
      var tasks = kanban.getOrDefault(status, List.of());
      response.put(status.name(), tasks.stream().map(t -> TaskResponse.from(t, today)).toList());
    }
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping
  @Operation(summary = "Yeni board oluştur")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ResponseEntity<ApiResponse<BoardResponse>> createBoard(
      @Valid @RequestBody CreateBoardRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(BoardResponse.from(boardService.createBoard(req))));
  }
}
