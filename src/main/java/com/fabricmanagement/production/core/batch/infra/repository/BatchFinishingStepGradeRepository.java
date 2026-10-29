package com.fabricmanagement.production.core.batch.infra.repository;

import com.fabricmanagement.production.core.batch.domain.BatchFinishingStepGrade;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchFinishingStepGradeRepository
    extends JpaRepository<BatchFinishingStepGrade, UUID> {

  List<BatchFinishingStepGrade> findByTenantIdAndStepIdInAndIsActiveTrue(
      UUID tenantId, Collection<UUID> stepIds);
}
