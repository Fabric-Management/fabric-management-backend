package com.fabricmanagement.flowboard.board.infra.repository;

import com.fabricmanagement.flowboard.board.domain.BoardView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** BoardView repository. */
public interface BoardViewRepository extends JpaRepository<BoardView, UUID> {

  /** Board'daki tüm aktif görünümleri getirir. */
  List<BoardView> findAllByBoardIdAndIsActiveTrue(UUID boardId);

  /** Board'un varsayılan görünümünü getirir. */
  Optional<BoardView> findByBoardIdAndIsDefaultTrue(UUID boardId);

  /** Board'daki görünümleri is_default false yapar (yeni default set etmeden önce). */
  List<BoardView> findAllByBoardIdAndIsDefaultTrue(UUID boardId);
}
