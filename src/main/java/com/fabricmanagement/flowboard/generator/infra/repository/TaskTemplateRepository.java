package com.fabricmanagement.flowboard.generator.infra.repository;

import com.fabricmanagement.flowboard.generator.domain.TaskTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** TaskTemplate repository. */
public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, UUID> {

  /** Event tipine göre aktif template'leri getirir — birden fazla template olabilir. */
  List<TaskTemplate> findByEventTypeAndIsActiveTrue(String eventType);

  /** Tüm aktif template'leri getirir (admin UI için). */
  List<TaskTemplate> findAllByIsActiveTrue();
}
