package com.fabricmanagement.flowboard.board.api;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * FlowBoard board yönetimi API.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md}
 */
@RestController
@RequestMapping("/api/flowboard/boards")
@RequiredArgsConstructor
@Tag(name = "FlowBoard — Board", description = "Board yönetimi ve kanban görünümü")
public class BoardController {

  private final BoardService boardService;
  private final TaskService taskService;
  private final Clock clock;

  @GetMapping
  @Operation(summary = "Tüm aktif board'ları listele")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<BoardResponse>> getAllBoards() {
    return ResponseEntity.ok(
        boardService.getAllBoards().stream().map(BoardResponse::from).toList());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Board detayı")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<BoardResponse> getBoard(@PathVariable UUID id) {
    return ResponseEntity.ok(BoardResponse.from(boardService.getBoard(id)));
  }

  @GetMapping("/{id}/tasks")
  @Operation(summary = "Board'daki task'ları getir — priorityScore DESC sıralı")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<TaskResponse>> getTasksByBoard(@PathVariable UUID id) {
    LocalDate today = LocalDate.now(clock);
    return ResponseEntity.ok(
        taskService.getTasksByBoard(id).stream().map(t -> TaskResponse.from(t, today)).toList());
  }

  /**
   * [X2 FIX] Wildcard return type → tipli Map. [F3 FIX] Tüm status'ler dahil — boş board'da bile
   * tüm kanban kolonları görünsün.
   */
  @GetMapping("/{id}/kanban")
  @Operation(summary = "Kanban view — status bazlı task grupları")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, List<TaskResponse>>> getKanbanView(@PathVariable UUID id) {
    var kanban = taskService.getKanbanView(id);
    LocalDate today = LocalDate.now(clock);
    // TaskStatus → String key dönüşümü
    Map<String, List<TaskResponse>> response = new LinkedHashMap<>();
    for (TaskStatus status : TaskStatus.values()) {
      var tasks = kanban.getOrDefault(status, List.of());
      response.put(status.name(), tasks.stream().map(t -> TaskResponse.from(t, today)).toList());
    }
    return ResponseEntity.ok(response);
  }

  @PostMapping
  @Operation(summary = "Yeni board oluştur (Admin)")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ResponseEntity<BoardResponse> createBoard(@Valid @RequestBody CreateBoardRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(BoardResponse.from(boardService.createBoard(req)));
  }
}
