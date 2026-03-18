package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.RecurringTaskTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTaskTemplateRepository
    extends JpaRepository<RecurringTaskTemplate, UUID> {

  List<RecurringTaskTemplate> findByTenantIdAndBoardIdAndDeletedAtIsNull(
      UUID tenantId, UUID boardId);

  @Query(
      "SELECT r FROM RecurringTaskTemplate r WHERE r.deletedAt IS NULL AND r.isActive = true AND r.nextTriggerAt <= :now AND (:tenantId IS NULL OR r.tenantId = :tenantId)")
  List<RecurringTaskTemplate> findDueTemplates(
      @Param("tenantId") UUID tenantId, @Param("now") OffsetDateTime now);

  // [O9 FIX] Paginated versiyon — scheduler batch işlemleri için
  @Query(
      "SELECT r FROM RecurringTaskTemplate r WHERE r.deletedAt IS NULL AND r.isActive = true AND r.nextTriggerAt <= :now AND (:tenantId IS NULL OR r.tenantId = :tenantId)")
  Page<RecurringTaskTemplate> findDueTemplates(
      @Param("tenantId") UUID tenantId, @Param("now") OffsetDateTime now, Pageable pageable);
}
