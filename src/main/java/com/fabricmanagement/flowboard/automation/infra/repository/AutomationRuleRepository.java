package com.fabricmanagement.flowboard.automation.infra.repository;

import com.fabricmanagement.flowboard.automation.domain.AutomationRule;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** AutomationRule repository. */
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

  /**
   * Board'a özel + global (boardId=null) aktif kuralları getirir — trigger tipine göre filtreler.
   *
   * <p>Global kurallar (boardId IS NULL) tüm board'larda çalışır.
   */
  @Query(
      """
      SELECT r FROM AutomationRule r
      WHERE r.isActive = true
        AND r.triggerType = :triggerType
        AND (r.boardId = :boardId OR r.boardId IS NULL)
      ORDER BY r.createdAt ASC
      """)
  List<AutomationRule> findActiveByTriggerTypeAndBoard(
      @Param("triggerType") AutomationTriggerType triggerType, @Param("boardId") UUID boardId);

  /** Tüm aktif kurallar (admin UI için). */
  List<AutomationRule> findAllByIsActiveTrueOrderByCreatedAtAsc();
}
