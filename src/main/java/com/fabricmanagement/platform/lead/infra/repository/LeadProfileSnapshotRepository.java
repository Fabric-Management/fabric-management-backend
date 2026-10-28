package com.fabricmanagement.platform.lead.infra.repository;

import com.fabricmanagement.platform.lead.domain.LeadProfileSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadProfileSnapshotRepository extends JpaRepository<LeadProfileSnapshot, UUID> {
  List<LeadProfileSnapshot> findByLeadIdOrderByRevisionAsc(UUID leadId);
}
