package com.fabricmanagement.flowboard.generator.infra.repository;

import com.fabricmanagement.flowboard.generator.domain.TaskTemplate;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** TaskTemplate repository. */
public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, UUID> {

  /** Event tipine göre aktif template'leri getirir — birden fazla template olabilir. */
  List<TaskTemplate> findByEventTypeAndIsActiveTrue(String eventType);

  /** Tüm aktif template'leri getirir (admin UI için). */
  List<TaskTemplate> findAllByIsActiveTrue();

  /** Template API: soft-deleted rows are not listed (TASK-TEMPLATE-TENANCY-1 plan §2.3). */
  List<TaskTemplate> findAllByDeletedAtIsNull();

  /** Template API: a soft-deleted row is not found (404), so it can never be reactivated. */
  Optional<TaskTemplate> findByIdAndDeletedAtIsNull(UUID id);

  /**
   * Catalogue reconciliation: all rows of one tenant for one job, in every state (deleted ones
   * included). The explicit tenant filter keeps the result correct on a connection that bypasses
   * RLS (integration tests run as superuser); under {@code fabric_app} RLS applies as well.
   */
  List<TaskTemplate> findAllByTenantIdAndEventTypeAndTaskType(
      UUID tenantId, String eventType, TaskType taskType);

  /** Catalogue reconciliation: the tenant's keyed row, in every state. */
  Optional<TaskTemplate> findByTenantIdAndCatalogKey(UUID tenantId, String catalogKey);
}
