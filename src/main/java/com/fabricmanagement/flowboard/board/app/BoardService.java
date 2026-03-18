package com.fabricmanagement.flowboard.board.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.board.domain.Board;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.domain.ViewType;
import com.fabricmanagement.flowboard.board.dto.CreateBoardRequest;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Board yönetim servisi.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — Bölüm 1 Board
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BoardService {

  private final BoardRepository boardRepo;

  /** Tenant'ın tüm aktif board'larını listeler. */
  @Transactional(readOnly = true)
  public List<Board> getAllBoards() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return boardRepo.findAllByTenantIdAndIsActiveTrue(tenantId);
  }

  /** Board detayını getirir. */
  @Transactional(readOnly = true)
  public Board getBoard(UUID boardId) {
    return boardRepo
        .findById(boardId)
        .orElseThrow(() -> new EntityNotFoundException("Board not found: " + boardId));
  }

  /** Belirli tipte board getirir. */
  @Transactional(readOnly = true)
  public Board getBoardByType(BoardType boardType) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return boardRepo
        .findByTenantIdAndBoardType(tenantId, boardType)
        .orElseThrow(() -> new EntityNotFoundException("Board not found for type: " + boardType));
  }

  /** Yeni board oluşturur — genellikle tenant onboard sırasında çağrılır. */
  @Transactional
  public Board createBoard(CreateBoardRequest req) {
    Board board =
        Board.create(
            req.name(),
            req.boardType(),
            req.wipLimitDefault(),
            req.defaultViewType() != null ? req.defaultViewType() : ViewType.KANBAN,
            req.description());
    Board saved = boardRepo.save(board);
    log.info("Board created: type={} id={}", req.boardType(), saved.getId());
    return saved;
  }

  /** WIP limitini günceller. */
  @Transactional
  public Board updateWipLimit(UUID boardId, Integer wipLimit) {
    Board board = getBoard(boardId);
    board.updateWipLimit(wipLimit);
    return boardRepo.save(board);
  }
}
