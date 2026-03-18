package com.fabricmanagement.flowboard.board.infra.repository;

import com.fabricmanagement.flowboard.board.domain.Board;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Board repository. */
public interface BoardRepository extends JpaRepository<Board, UUID> {

  /** Tenant'ın tüm aktif board'larını getirir. */
  List<Board> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

  /** Tenant + board tipi ile board bulur (tenant başına bir tane — unique). */
  Optional<Board> findByTenantIdAndBoardType(UUID tenantId, BoardType boardType);

  /** Board'un yöneticisinin ID'sini getirir. */
  @Query("SELECT b.managerUserId FROM Board b WHERE b.id = :boardId")
  Optional<UUID> findManagerUserId(@Param("boardId") UUID boardId);

  /**
   * Verilen boardId listesi için (boardId → managerUserId) projeksiyonunu döner. N+1 önleme:
   * EscalationJob sayfa başına tek sorguda tüm board yöneticilerini çeker.
   */
  @Query(
      "SELECT b.id AS boardId, b.managerUserId AS managerUserId FROM Board b WHERE b.id IN :boardIds")
  List<BoardManagerProjection> findManagerUserIdsByBoardIds(
      @Param("boardIds") Collection<UUID> boardIds);

  /** boardId → managerUserId projeksiyonu (EscalationJob batch lookup). */
  interface BoardManagerProjection {
    UUID getBoardId();

    UUID getManagerUserId();
  }
}
