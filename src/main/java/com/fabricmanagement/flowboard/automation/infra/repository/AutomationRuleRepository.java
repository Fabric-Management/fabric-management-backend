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

  /** İlgili tenant'ın tüm kurallarını getirmek için (UI Liste ekranı). */
  List<AutomationRule> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  /**
   * İlgili tenant'a ve board'a ait kuralları getirmek için. boardId bulunamazsa null dönülüp global
   * kurallar da isteneceğinden OR mantığı.
   */
  @Query(
      """
      SELECT r FROM AutomationRule r
      WHERE r.tenantId = :tenantId
        AND (r.boardId = :boardId OR r.boardId IS NULL)
      ORDER BY r.createdAt DESC
      """)
  List<AutomationRule> findAllByTenantIdAndBoardId(
      @Param("tenantId") UUID tenantId, @Param("boardId") UUID boardId);

  /** Tenant filtresiyle spesifik bir kuralı getirmek için. */
  java.util.Optional<AutomationRule> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Board'a özel + global (boardId=null) aktif kuralları getirir — trigger tipine göre filtreler.
   * [GÜVENLİK YAMASI] Tenant izolasyonu sağlandı.
   */
  @Query(
      """
      SELECT r FROM AutomationRule r
      WHERE r.isActive = true
        AND r.tenantId = :tenantId
        AND r.triggerType = :triggerType
        AND (r.boardId = :boardId OR r.boardId IS NULL)
      ORDER BY r.createdAt ASC
      """)
  List<AutomationRule> findActiveByTenantAndTriggerTypeAndBoard(
      @Param("tenantId") UUID tenantId,
      @Param("triggerType") AutomationTriggerType triggerType,
      @Param("boardId") UUID boardId);

  /** Tüm aktif kurallar (admin UI için). */
  List<AutomationRule> findAllByIsActiveTrueOrderByCreatedAtAsc();
}
