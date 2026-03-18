package com.fabricmanagement.flowboard.board.infra.repository;

import com.fabricmanagement.flowboard.board.domain.BoardGroup;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** BoardGroup repository. */
public interface BoardGroupRepository extends JpaRepository<BoardGroup, UUID> {

  /** Board'daki tüm aktif grupları displayOrder'a göre sıralar. */
  List<BoardGroup> findAllByBoardIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID boardId);
}
