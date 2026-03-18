package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.TaskLabel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** TaskLabel repository. */
public interface TaskLabelRepository extends JpaRepository<TaskLabel, UUID> {

  /**
   * Tenant için kullanılabilir tüm etiketleri getirir: global etiketler (board_id = null) + bu
   * board'a özel etiketler.
   */
  @Query(
      """
      SELECT l FROM TaskLabel l
      WHERE l.tenantId = :tenantId
        AND l.isActive = true
        AND (l.boardId IS NULL OR l.boardId = :boardId)
      ORDER BY l.name
      """)
  List<TaskLabel> findAvailableLabels(
      @Param("tenantId") UUID tenantId, @Param("boardId") UUID boardId);

  /** Global etiketleri isimle bulur (seed lookup). */
  Optional<TaskLabel> findByTenantIdAndNameAndBoardIdIsNull(UUID tenantId, String name);

  /**
   * [O5 FIX] İsme göre doğrudan DB sorgusu — tüm label'ları belleğe yüklemek yerine. Global
   * etiketler (boardId = null) ve board-specific etiketler dahil.
   */
  @Query(
      """
      SELECT l FROM TaskLabel l
      WHERE l.tenantId = :tenantId
        AND l.isActive = true
        AND (l.boardId IS NULL OR l.boardId = :boardId)
        AND LOWER(l.name) = LOWER(:name)
      """)
  Optional<TaskLabel> findByTenantIdAndBoardIdAndNameIgnoreCase(
      @Param("tenantId") UUID tenantId, @Param("boardId") UUID boardId, @Param("name") String name);
}
